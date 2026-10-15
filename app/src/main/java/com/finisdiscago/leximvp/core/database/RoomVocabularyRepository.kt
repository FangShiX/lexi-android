package com.finisdiscago.leximvp.core.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.finisdiscago.leximvp.core.database.entity.ReviewCardEntity
import com.finisdiscago.leximvp.core.database.entity.ReviewStateEntity
import com.finisdiscago.leximvp.core.database.entity.LearningAttemptEntity
import com.finisdiscago.leximvp.core.database.entity.VocabularyBookEntity
import com.finisdiscago.leximvp.core.database.entity.VocabularyBookType
import com.finisdiscago.leximvp.core.database.entity.WordEntryEntity
import com.finisdiscago.leximvp.core.database.entity.WordIntakeStatus
import com.finisdiscago.leximvp.core.database.entity.WordFamiliarity
import com.finisdiscago.leximvp.core.database.entity.SourceRecordEntity
import com.finisdiscago.leximvp.core.database.entity.EntrySourceEntity
import com.finisdiscago.leximvp.core.text.WordNormalizer
import com.finisdiscago.leximvp.domain.model.CardType
import com.finisdiscago.leximvp.domain.model.VocabularyBook
import com.finisdiscago.leximvp.domain.model.WordEntry
import com.finisdiscago.leximvp.domain.repository.DuplicateWordException
import com.finisdiscago.leximvp.domain.repository.VocabularyRepository
import com.finisdiscago.leximvp.domain.repository.VocabularyValidationException
import com.finisdiscago.leximvp.domain.repository.WordInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomVocabularyRepository(
    private val database: LexiDatabase,
    private val now: () -> Long = System::currentTimeMillis
) : VocabularyRepository {
    private val vocabularyDao = database.vocabularyDao()
    private val reviewDao = database.reviewDao()

    override fun observeBooks(): Flow<List<VocabularyBook>> =
        vocabularyDao.observeBooks().map { books -> books.map { it.toDomain() } }

    override suspend fun getBook(bookId: Long): VocabularyBook? {
        val book = vocabularyDao.getBook(bookId) ?: return null
        return VocabularyBook(
            id = book.id,
            name = book.name,
            description = book.description,
            wordCount = vocabularyDao.getWordCount(bookId),
            learnedWordCount = vocabularyDao.getLearnedWordCount(bookId),
            bookType = book.bookType,
            isProtected = book.isProtected,
            selectionEnabled = book.selectionEnabled,
            dailySelectionLimit = book.dailySelectionLimit,
            selectionPriority = book.selectionPriority,
            createdAtEpochMillis = book.createdAtEpochMillis,
            updatedAtEpochMillis = book.updatedAtEpochMillis
        )
    }

    override suspend fun createBook(name: String, description: String): Long {
        val cleanName = validateBookName(name)
        val timestamp = now()
        return try {
            val bookId = vocabularyDao.insertBook(
                VocabularyBookEntity(
                    name = cleanName,
                    description = description.trim(),
                    bookType = VocabularyBookType.USER,
                    createdOrder = timestamp,
                    createdAtEpochMillis = timestamp,
                    updatedAtEpochMillis = timestamp
                )
            )
            vocabularyDao.upsertSource(SourceRecordEntity(
                id = bookId, stableId = "local-source:$bookId", name = cleanName,
                description = description.trim(), sourceType = "user",
                createdAtEpochMillis = timestamp, updatedAtEpochMillis = timestamp
            ))
            bookId
        } catch (_: SQLiteConstraintException) {
            throw VocabularyValidationException("已存在同名词库")
        }
    }

    override suspend fun updateBook(bookId: Long, name: String, description: String) {
        val existing = vocabularyDao.getBook(bookId)
            ?: throw VocabularyValidationException("词库不存在")
        try {
            vocabularyDao.updateBook(
                existing.copy(
                    name = validateBookName(name),
                    description = description.trim(),
                    updatedAtEpochMillis = now()
                )
            )
            vocabularyDao.getSource(bookId)?.let { source ->
                vocabularyDao.upsertSource(source.copy(
                    name = validateBookName(name),
                    description = description.trim(),
                    updatedAtEpochMillis = now()
                ))
            }
        } catch (_: SQLiteConstraintException) {
            throw VocabularyValidationException("已存在同名词库")
        }
    }

    override suspend fun updateBookPlan(
        bookId: Long,
        selectionEnabled: Boolean,
        dailySelectionLimit: Int,
        selectionPriority: Int
    ) {
        val existing = vocabularyDao.getBook(bookId)
            ?: throw VocabularyValidationException("词书不存在")
        vocabularyDao.updateBook(
            existing.copy(
                selectionEnabled = selectionEnabled,
                dailySelectionLimit = dailySelectionLimit.coerceIn(0, 200),
                selectionPriority = selectionPriority.coerceIn(-100, 100),
                updatedAtEpochMillis = now()
            )
        )
    }

    override suspend fun deleteBook(bookId: Long) {
        val book = vocabularyDao.getBook(bookId) ?: return
        if (book.isProtected) {
            throw VocabularyValidationException("系统词书不能删除")
        }
        database.withTransaction {
            val fallback = vocabularyDao.getBookByType(VocabularyBookType.SYSTEM_READING_UNKNOWNS)
                ?: throw VocabularyValidationException("统一词库系统容器不存在")
            vocabularyDao.rehomeOwnedWords(bookId, fallback.id)
            vocabularyDao.deleteSource(bookId)
            vocabularyDao.deleteBookById(bookId)
        }
    }

    override fun observeWords(bookId: Long, query: String): Flow<List<WordEntry>> {
        val source = if (query.isBlank()) {
            vocabularyDao.observeWords(bookId)
        } else {
            vocabularyDao.searchWords(bookId, query.trim())
        }
        return source.map { words -> words.map { it.toDomain() } }
    }

    override suspend fun getWord(wordId: Long): WordEntry? =
        vocabularyDao.getWord(wordId)?.toDomain()

    override suspend fun createWord(input: WordInput): Long {
        val validated = validateWord(input)
        return database.withTransaction {
            ensureWordIsUnique(validated.normalizedWord, validated.language, validated.homographKey)
            val timestamp = now()
            val sourceOrder = vocabularyDao.nextSourceOrder(validated.bookId)
            val wordId = vocabularyDao.insertWord(
                validated.toEntity(
                    timestamp = timestamp,
                    sourceOrder = sourceOrder
                )
            )
            vocabularyDao.insertEntrySources(listOf(EntrySourceEntity(
                wordId = wordId,
                sourceId = validated.bookId,
                sourceOrder = sourceOrder,
                evidenceStatus = "user",
                addedAtEpochMillis = timestamp
            )))
            val cardIds = reviewDao.insertCards(
                (if (validated.meaning.isBlank()) emptyList() else listOf(CardType.ForeignToMeaning, CardType.MeaningToForeign, CardType.AudioToSpelling)).map { type ->
                    ReviewCardEntity(
                        stableId = "${validated.toEntity(timestamp = timestamp, sourceOrder = sourceOrder).stableId}:card:${type.name}",
                        wordId = wordId,
                        cardType = type.name,
                        createdAtEpochMillis = timestamp
                    )
                }
            )
            reviewDao.upsertStates(
                cardIds.map { cardId ->
                    ReviewStateEntity(
                        cardId = cardId,
                        status = "New",
                        dueAtEpochMillis = timestamp
                    )
                }
            )
            touchBook(validated.bookId, timestamp)
            wordId
        }
    }

    override suspend fun updateWord(wordId: Long, input: WordInput) {
        val existing = vocabularyDao.getWord(wordId)
            ?: throw VocabularyValidationException("词条不存在")
        val validated = validateWord(input)
        database.withTransaction {
            ensureWordIsUnique(
                normalizedWord = validated.normalizedWord,
                language = validated.language,
                homographKey = validated.homographKey,
                excludedWordId = wordId
            )
            val timestamp = now()
            val sourceOrder = vocabularyDao.nextSourceOrder(validated.bookId)
            vocabularyDao.updateWord(
                validated.toEntity(
                    timestamp = timestamp,
                    id = wordId,
                    createdAt = existing.createdAtEpochMillis,
                    sourceOrder = existing.sourceOrder
                ).copy(
                    stableId = existing.stableId,
                    audioUri = existing.audioUri,
                    richContentJson = existing.richContentJson,
                    intakeStatus = existing.intakeStatus,
                    queuedAtEpochMillis = existing.queuedAtEpochMillis,
                    manualFamiliarity = existing.manualFamiliarity,
                    proficiencyScore = existing.proficiencyScore,
                    lastSeenAtEpochMillis = existing.lastSeenAtEpochMillis
                )
            )
            vocabularyDao.insertEntrySources(listOf(EntrySourceEntity(
                wordId = wordId,
                sourceId = validated.bookId,
                sourceOrder = sourceOrder,
                evidenceStatus = "user",
                addedAtEpochMillis = timestamp
            )))
            touchBook(validated.bookId, timestamp)
        }
    }

    override suspend fun setWordFamiliarity(wordId: Long, familiarity: String?) {
        val normalized = familiarity?.takeIf {
            it in setOf(
                WordFamiliarity.UNKNOWN,
                WordFamiliarity.LEARNING,
                WordFamiliarity.FAMILIAR,
                WordFamiliarity.MASTERED
            )
        }
        if (familiarity != null && normalized == null) {
            throw VocabularyValidationException("熟知度无效")
        }
        database.withTransaction {
            val timestamp = now()
            vocabularyDao.updateWordFamiliarity(wordId, normalized, timestamp)
            reviewDao.insertAttempt(
                LearningAttemptEntity(
                    sessionId = "familiarity:$timestamp",
                    wordId = wordId,
                    rating = when (normalized) {
                        WordFamiliarity.MASTERED -> 3
                        WordFamiliarity.FAMILIAR -> 2
                        WordFamiliarity.LEARNING -> 1
                        else -> 0
                    },
                    responseTimeMillis = 0,
                    attemptKind = if (normalized == null) "ManualFamiliarityCleared" else "ManualFamiliarity",
                    attemptedAtEpochMillis = timestamp
                )
            )
            if (normalized == null) {
                WordAssessmentService(database).recompute(wordId, timestamp)
            } else {
                val mastered = normalized == WordFamiliarity.MASTERED
                val familiar = normalized == WordFamiliarity.FAMILIAR
                reviewDao.getStatesForWord(wordId).forEach { state ->
                    reviewDao.updateState(state.copy(
                        status = if (mastered) "Mastered" else if (familiar) "Review" else "Relearning",
                        intervalDays = if (mastered) 30.0 else if (familiar) 4.0 else 0.0,
                        dueAtEpochMillis = timestamp + if (mastered) 30L * 86400000 else if (familiar) 4L * 86400000 else 0L
                    ))
                }
                WordAssessmentService(database).recompute(wordId, timestamp)
            }
        }
    }

    override suspend fun deleteWord(wordId: Long) {
        database.withTransaction {
            val existing = vocabularyDao.getWord(wordId) ?: return@withTransaction
            vocabularyDao.deleteWordById(wordId)
            touchBook(existing.bookId, now())
        }
    }

    private suspend fun ensureWordIsUnique(
        normalizedWord: String,
        language: String,
        homographKey: String,
        excludedWordId: Long = 0
    ) {
        if (vocabularyDao.wordExists(normalizedWord, language, homographKey, excludedWordId)) {
            throw DuplicateWordException(normalizedWord)
        }
    }

    private suspend fun touchBook(bookId: Long, timestamp: Long) {
        val book = vocabularyDao.getBook(bookId) ?: return
        vocabularyDao.updateBook(book.copy(updatedAtEpochMillis = timestamp))
    }

    private fun validateBookName(name: String): String {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            throw VocabularyValidationException("词库名称不能为空")
        }
        return cleanName
    }

    private fun validateWord(input: WordInput): ValidatedWord {
        val cleanWord = input.word.trim()
        val cleanMeaning = input.meaning.trim()
        if (cleanWord.isEmpty()) {
            throw VocabularyValidationException("单词不能为空")
        }
        val normalizedWord = WordNormalizer.normalizeHeadword(cleanWord)
        if (normalizedWord.isEmpty()) {
            throw VocabularyValidationException("请输入有效单词")
        }
        return ValidatedWord(
            bookId = input.bookId,
            word = cleanWord,
            normalizedWord = normalizedWord,
            meaning = cleanMeaning,
            concept = input.concept.trim().ifEmpty { null },
            pronunciation = input.pronunciation.trim().ifEmpty { null },
            exampleSentence = input.exampleSentence.trim().ifEmpty { null },
            exampleTranslation = input.exampleTranslation.trim().ifEmpty { null },
            synonyms = input.synonyms.trim().ifEmpty { null },
            tags = input.tags.map(String::trim).filter(String::isNotEmpty).distinct(),
            language = input.language.trim().lowercase().ifBlank { "en" },
            homographKey = input.homographKey.trim()
        )
    }

    private data class ValidatedWord(
        val bookId: Long,
        val word: String,
        val normalizedWord: String,
        val meaning: String,
        val concept: String?,
        val pronunciation: String?,
        val exampleSentence: String?,
        val exampleTranslation: String?,
        val synonyms: String?,
        val tags: List<String>,
        val language: String,
        val homographKey: String
    ) {
        fun toEntity(
            timestamp: Long,
            id: Long = 0,
            createdAt: Long = timestamp,
            sourceOrder: Int = 0
        ): WordEntryEntity = WordEntryEntity(
            id = id,
            bookId = bookId,
            word = word,
            normalizedWord = normalizedWord,
            meaning = meaning,
            concept = concept,
            pronunciation = pronunciation,
            exampleSentence = exampleSentence,
            exampleTranslation = exampleTranslation,
            synonyms = synonyms,
            tags = tags.joinToString(",").ifEmpty { null },
            richContentJson = null,
            stableId = "lexi:$language:$normalizedWord${homographKey.takeIf(String::isNotBlank)?.let { ":$it" }.orEmpty()}",
            language = language,
            entryKind = if (word.any(Char::isWhitespace)) "phrase" else "word",
            contentStatus = if (meaning.isBlank()) "incomplete" else "user",
            homographKey = homographKey,
            sourceOrder = sourceOrder,
            intakeStatus = if (meaning.isBlank()) WordIntakeStatus.PENDING else WordIntakeStatus.QUEUED,
            queuedAtEpochMillis = timestamp.takeUnless { meaning.isBlank() },
            createdAtEpochMillis = createdAt,
            updatedAtEpochMillis = timestamp
        )
    }
}
