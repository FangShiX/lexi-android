package com.finisdiscago.leximvp.core.database

import androidx.room.withTransaction
import com.finisdiscago.leximvp.core.database.entity.ArticleEntity
import com.finisdiscago.leximvp.core.database.entity.ArticleTokenEntity
import com.finisdiscago.leximvp.core.database.entity.WordFormEntity
import com.finisdiscago.leximvp.core.text.ArticleTokenizer
import com.finisdiscago.leximvp.core.text.WordNormalizer
import com.finisdiscago.leximvp.domain.model.Article
import com.finisdiscago.leximvp.domain.model.ArticleReading
import com.finisdiscago.leximvp.domain.model.ArticleToken
import com.finisdiscago.leximvp.domain.model.ArticleWordStatus
import com.finisdiscago.leximvp.domain.model.ReadingMark
import com.finisdiscago.leximvp.domain.model.WordMatchCandidate
import com.finisdiscago.leximvp.domain.repository.ArticleRepository
import com.finisdiscago.leximvp.domain.repository.VocabularyRepository
import com.finisdiscago.leximvp.domain.repository.VocabularyValidationException
import com.finisdiscago.leximvp.domain.repository.WordInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class RoomArticleRepository(
    private val database: LexiDatabase,
    private val vocabularyRepository: VocabularyRepository,
    private val now: () -> Long = System::currentTimeMillis
) : ArticleRepository {
    private val articleDao = database.articleDao()
    private val vocabularyDao = database.vocabularyDao()
    private val reviewDao = database.reviewDao()

    override fun observeArticles(): Flow<List<Article>> =
        articleDao.observeArticles().map { articles -> articles.map { it.toDomain() } }

    override suspend fun createArticle(
        title: String,
        content: String,
        source: String
    ): Long {
        val cleanTitle = title.trim()
        val cleanContent = content.trim()
        if (cleanTitle.isEmpty()) {
            throw VocabularyValidationException("文章标题不能为空")
        }
        if (cleanContent.isEmpty()) {
            throw VocabularyValidationException("文章正文不能为空")
        }
        return database.withTransaction {
            val timestamp = now()
            val articleId = articleDao.insertArticle(
                ArticleEntity(
                    title = cleanTitle,
                    content = cleanContent,
                    source = source.trim().ifEmpty { null },
                    createdAtEpochMillis = timestamp,
                    updatedAtEpochMillis = timestamp
                )
            )
            val tokens = ArticleTokenizer.tokenize(cleanContent)
            val candidates = tokens.associateWith { WordNormalizer.lookupCandidates(it.rawText) }
            val matchCandidates = candidates.values.flatten().distinct().chunked(MATCH_CHUNK_SIZE)
                .flatMap { vocabularyDao.findLearningMatches(it) }
                .groupBy { it.matchedText }
            // An exact/form match is automatic only when it resolves to one lexical entry.
            // Homographs remain unlinked so the reader can ask the user to choose a sense entry.
            val matches = matchCandidates.mapNotNull { (text, values) ->
                values.distinctBy { it.id }.singleOrNull()?.let { text to it }
            }.toMap()
            val observedForms = linkedMapOf<String, WordFormEntity>()
            val tokenEntities = tokens.map { token ->
                    val match = candidates.getValue(token).firstNotNullOfOrNull(matches::get)
                    if (match != null && token.headwordText != match.normalizedWord) {
                        val form = WordFormEntity(
                            wordId = match.id,
                            form = token.rawText,
                            normalizedForm = token.headwordText,
                            relationType = "observed_inflection",
                            source = "reading"
                        )
                        observedForms["${form.wordId}:${form.normalizedForm}"] = form
                    }
                    ArticleTokenEntity(
                        articleId = articleId,
                        rawText = token.rawText,
                        normalizedText = token.normalizedText,
                        startOffset = token.startOffset,
                        endOffset = token.endOffset,
                        wordId = match?.id,
                        learningStatus = match?.learningStatus.toArticleStatus().name
                    )
                }
            observedForms.values.chunked(TOKEN_CHUNK_SIZE).forEach { vocabularyDao.insertWordForms(it) }
            tokenEntities.chunked(TOKEN_CHUNK_SIZE).forEach { articleDao.insertTokens(it) }
            articleId
        }
    }

    override suspend fun deleteArticle(articleId: Long) {
        articleDao.deleteArticleById(articleId)
    }

    override suspend fun getReading(articleId: Long): ArticleReading? {
        val article = articleDao.getArticle(articleId) ?: return null
        val tokens = articleDao.getTokens(articleId)
        val words = tokens.flatMap { WordNormalizer.lookupCandidates(it.rawText) }.distinct()
            .chunked(MATCH_CHUNK_SIZE).flatMap { vocabularyDao.findLearningMatches(it) }.associateBy { it.id }
        return ArticleReading(
            article = article.toDomain(),
            tokens = tokens.map { token ->
                val word = token.wordId?.let(words::get)
                val status = when (token.learningStatus) {
                    ArticleWordStatus.Known.name -> ArticleWordStatus.Known
                    ArticleWordStatus.Learning.name -> ArticleWordStatus.Learning
                    else -> word?.learningStatus.toArticleStatus()
                }
                ArticleToken(
                    id = token.id,
                    articleId = token.articleId,
                    rawText = token.rawText,
                    normalizedText = token.normalizedText,
                    startOffset = token.startOffset,
                    endOffset = token.endOffset,
                    wordId = token.wordId,
                    status = status,
                    meaning = word?.meaning,
                    pronunciation = word?.pronunciation
                )
            }
        )
    }

    override suspend fun updateReadingPosition(articleId: Long, offset: Int) {
        articleDao.updateReadingPosition(articleId, offset.coerceAtLeast(0), now())
    }

    override suspend fun updateArticleContent(articleId: Long, content: String) {
        val clean = content.trim()
        if (clean.isBlank()) throw VocabularyValidationException("正文不能为空")
        database.withTransaction {
            val article = articleDao.getArticle(articleId) ?: throw VocabularyValidationException("文章不存在")
            val previous = articleDao.getTokens(articleId).groupBy { it.normalizedText }.mapValues { (_, values) ->
                values.firstOrNull { it.wordId != null } ?: values.first()
            }
            val parsed = ArticleTokenizer.tokenize(clean)
            val candidates = parsed.associateWith { WordNormalizer.lookupCandidates(it.rawText) }
            val matches = candidates.values.flatten().distinct().chunked(MATCH_CHUNK_SIZE)
                .flatMap { vocabularyDao.findLearningMatches(it) }.groupBy { it.matchedText }
                .mapNotNull { (text, values) -> values.distinctBy { it.id }.singleOrNull()?.let { text to it } }.toMap()
            val corrected = parsed.map { token ->
                val old = previous[token.normalizedText]
                val match = candidates.getValue(token).firstNotNullOfOrNull(matches::get)
                ArticleTokenEntity(
                    articleId = articleId, rawText = token.rawText, normalizedText = token.normalizedText,
                    startOffset = token.startOffset, endOffset = token.endOffset,
                    wordId = old?.wordId ?: match?.id,
                    learningStatus = old?.learningStatus ?: match?.learningStatus.toArticleStatus().name
                )
            }
            articleDao.updateArticle(article.copy(content = clean, updatedAtEpochMillis = now(), lastReadOffset = article.lastReadOffset.coerceAtMost(clean.length)))
            articleDao.replaceTokens(articleId, corrected)
        }
    }

    override suspend fun getReadingMarks(articleId: Long): List<ReadingMark> {
        val article = articleDao.getArticle(articleId) ?: return emptyList()
        val key = article.documentKey()
        return database.lexiconDao().allRecords().asSequence().filter { it.kind == "reading_mark" }.mapNotNull { record ->
            runCatching { JSONObject(record.payload) }.getOrNull()?.takeIf {
                it.optLong("articleId") == articleId || it.optString("documentKey") == key
            }?.let { ReadingMark(record.id, articleId, it.optString("kind", "bookmark"), it.optInt("offset"), it.optString("snippet"), it.optString("note"), record.updatedAtEpochMillis) }
        }.sortedBy { it.offset }.toList()
    }

    override suspend fun addReadingMark(articleId: Long, kind: String, offset: Int, note: String): Long {
        val article = articleDao.getArticle(articleId) ?: throw VocabularyValidationException("文章不存在")
        val safeOffset = offset.coerceIn(0, article.content.length)
        val snippet = article.content.substring(safeOffset, (safeOffset + 100).coerceAtMost(article.content.length)).replace('\n', ' ').trim()
        val timestamp = now()
        return database.lexiconDao().save(com.finisdiscago.leximvp.core.database.entity.WorkspaceRecordEntity(
            kind = "reading_mark", title = if (kind == "note") "阅读标注" else "阅读书签",
            payload = JSONObject().put("articleId", articleId).put("documentKey", article.documentKey()).put("kind", kind)
                .put("offset", safeOffset).put("snippet", snippet).put("note", note.trim()).toString(),
            updatedAtEpochMillis = timestamp
        ))
    }

    override suspend fun deleteReadingMark(markId: Long) { database.lexiconDao().deleteRecord(markId) }

    override suspend fun findWordCandidates(text: String): List<WordMatchCandidate> {
        val forms = WordNormalizer.lookupCandidates(text)
        return vocabularyDao.findLearningMatches(forms).distinctBy { it.id }
            .map { WordMatchCandidate(it.id, it.word, it.meaning) }
    }

    override suspend fun linkWordCandidate(articleId: Long, normalizedText: String, wordId: Long) {
        val word = vocabularyDao.getWord(wordId) ?: throw VocabularyValidationException("候选词条不存在")
        val status = when (word.intakeStatus) {
            "Learning" -> ArticleWordStatus.Learning
            "Reviewing", "Mature" -> ArticleWordStatus.Learned
            else -> ArticleWordStatus.Unlearned
        }
        articleDao.linkWord(articleId, normalizedText, wordId, status.name)
    }

    private fun ArticleEntity.documentKey(): String = runCatching {
        val root = JSONObject(source.orEmpty())
        listOf(root.optString("assetPath"), root.optString("locator")).joinToString("#").takeIf { it != "#" }
    }.getOrNull() ?: "manual:$title:$createdAtEpochMillis"

    override suspend fun markKnown(articleId: Long, normalizedText: String) {
        database.withTransaction {
            articleDao.getTokens(articleId).firstOrNull { it.normalizedText == normalizedText }?.wordId?.let {
                vocabularyRepository.setWordFamiliarity(it, "Familiar")
            }
            articleDao.updateWordStatus(articleId, normalizedText, ArticleWordStatus.Known.name)
        }
    }

    override suspend fun markUnknown(
        articleId: Long,
        normalizedText: String,
        nowEpochMillis: Long
    ) {
        database.withTransaction {
            val token = articleDao.getTokens(articleId)
                .firstOrNull { it.normalizedText == normalizedText }
                ?: return@withTransaction
            val wordId = token.wordId
            if (wordId != null) {
                StudyCardFactory(database).ensure(wordId, nowEpochMillis)
                vocabularyDao.updateIntakeStatus(listOf(wordId), "Queued", nowEpochMillis)
                vocabularyDao.updateWordFamiliarity(wordId, null, nowEpochMillis)
                reviewDao.getStatesForWord(wordId).forEach { state ->
                    reviewDao.updateState(
                        state.copy(
                            status = "New",
                            dueAtEpochMillis = nowEpochMillis,
                            intervalDays = 0.0,
                            repetitionCount = 0,
                            lastReviewedAtEpochMillis = null
                        )
                    )
                }
                articleDao.updateWordStatus(
                    articleId,
                    normalizedText,
                    ArticleWordStatus.Learning.name
                )
            }
        }
    }

    override suspend fun addUnknownWord(
        articleId: Long,
        normalizedText: String,
        bookId: Long,
        meaning: String
    ): Long {
        val token = articleDao.getTokens(articleId)
            .firstOrNull { it.normalizedText == normalizedText }
            ?: throw VocabularyValidationException("文章单词不存在")
        return database.withTransaction {
            val wordId = vocabularyRepository.createWord(
                WordInput(
                    bookId = bookId,
                    word = token.normalizedText,
                    meaning = meaning
                )
            )
            val observedForm = WordNormalizer.normalizeHeadword(token.rawText)
            vocabularyDao.insertWordForms(listOf(WordFormEntity(
                wordId = wordId,
                form = token.rawText,
                normalizedForm = observedForm,
                relationType = if (observedForm == token.normalizedText) "observed_form" else "observed_inflection",
                source = "reading"
            )))
            articleDao.linkWord(
                articleId = articleId,
                normalizedText = normalizedText,
                wordId = wordId,
                learningStatus = ArticleWordStatus.Learning.name
            )
            wordId
        }
    }

    private fun ArticleEntity.toDomain() = Article(
        id = id,
        title = title,
        content = content,
        source = source,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        lastReadOffset = lastReadOffset
    )

    private fun String?.toArticleStatus(): ArticleWordStatus = when (this) {
        "New" -> ArticleWordStatus.Unlearned
        "Learning" -> ArticleWordStatus.Learning
        "Review", "Relearning", "Mastered" -> ArticleWordStatus.Learned
        else -> ArticleWordStatus.Unknown
    }

    private companion object {
        const val MATCH_CHUNK_SIZE = 700
        const val TOKEN_CHUNK_SIZE = 1_000
    }
}
