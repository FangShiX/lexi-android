package com.finisdiscago.leximvp.core.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.finisdiscago.leximvp.core.database.entity.ReviewCardEntity
import com.finisdiscago.leximvp.core.database.entity.ReviewStateEntity
import com.finisdiscago.leximvp.core.database.entity.VocabularyBookEntity
import com.finisdiscago.leximvp.core.database.entity.VocabularyBookType
import com.finisdiscago.leximvp.core.database.entity.WordEntryEntity
import com.finisdiscago.leximvp.core.database.entity.WordIntakeStatus
import com.finisdiscago.leximvp.core.database.entity.WordFormEntity
import com.finisdiscago.leximvp.core.database.entity.SourceRecordEntity
import com.finisdiscago.leximvp.core.database.entity.EntrySourceEntity
import com.finisdiscago.leximvp.core.database.entity.SenseEntity
import com.finisdiscago.leximvp.core.database.entity.ExampleEntity
import com.finisdiscago.leximvp.core.database.entity.EntryRelationEntity
import com.finisdiscago.leximvp.core.database.entity.EntryTagEntity
import com.finisdiscago.leximvp.core.database.entity.FrequencyRecordEntity
import com.finisdiscago.leximvp.core.text.WordNormalizer
import com.finisdiscago.leximvp.domain.model.CardType
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

data class VocabularyImportProgress(
    val importedEntries: Int,
    val totalEntries: Int
)

data class VocabularyImportResult(
    val bookId: Long,
    val bookName: String,
    val entryCount: Int,
    val skippedDuplicateCount: Int = 0,
    val updatedCount: Int = 0,
    val conflictCount: Int = 0
)

data class VocabularyImportLimits(
    val maxPackageBytes: Long = 512L * 1024 * 1024,
    val maxManifestBytes: Int = 128 * 1024,
    val maxEntryBytes: Int = 512 * 1024,
    val maxEntries: Int = 300_000,
    val maxExpandedBytes: Long = 2L * 1024 * 1024 * 1024
)

class VocabularyPackageException(message: String) : IllegalArgumentException(message)

class VocabularyPackageImporter(
    private val database: LexiDatabase,
    private val cacheDirectory: File,
    private val limits: VocabularyImportLimits = VocabularyImportLimits(),
    private val now: () -> Long = System::currentTimeMillis
) {
    suspend fun import(
        input: InputStream,
        onProgress: suspend (VocabularyImportProgress) -> Unit = {}
    ): VocabularyImportResult = withContext(Dispatchers.IO) {
        cacheDirectory.mkdirs()
        val temporaryFile = File.createTempFile("lexi-import-", ".lexipack", cacheDirectory)
        try {
            temporaryFile.outputStream().buffered().use { output ->
                input.buffered().use { source ->
                    source.copyToWithLimit(output, limits.maxPackageBytes)
                }
            }
            importFile(temporaryFile, onProgress)
        } finally {
            temporaryFile.delete()
        }
    }

    private suspend fun importFile(
        packageFile: File,
        onProgress: suspend (VocabularyImportProgress) -> Unit
    ): VocabularyImportResult = ZipFile(packageFile).use { zip ->
        val manifestEntry = zip.getEntry(MANIFEST_FILE)
            ?: throw VocabularyPackageException("词包缺少 $MANIFEST_FILE")
        if (manifestEntry.size > limits.maxManifestBytes) {
            throw VocabularyPackageException("词包清单过大")
        }
        val manifest = try {
            zip.getInputStream(manifestEntry).use { input ->
                JSONObject(input.readUtf8WithLimit(limits.maxManifestBytes, "词包清单过大"))
            }
        } catch (error: VocabularyPackageException) {
            throw error
        } catch (_: JSONException) {
            throw VocabularyPackageException("词包清单格式无效")
        }
        if (manifest.optInt("schemaVersion") !in 1..2) {
            throw VocabularyPackageException("不支持的词包版本")
        }
        val metadata = manifest.optJSONObject("book")
            ?: manifest.optJSONObject("source")
            ?: throw VocabularyPackageException("词包缺少来源信息")
        val bookName = metadata.requiredString("name")
        val expectedCount = metadata.optInt("entryCount", -1)
        if (expectedCount <= 0) {
            throw VocabularyPackageException("词包词数无效")
        }
        if (expectedCount > limits.maxEntries) {
            throw VocabularyPackageException(
                "词包包含 $expectedCount 词，超过上限 ${limits.maxEntries}"
            )
        }
        val entriesEntry = zip.getEntry(ENTRIES_FILE)
            ?: throw VocabularyPackageException("词包缺少 $ENTRIES_FILE")

        if (entriesEntry.size < 0 || entriesEntry.size > limits.maxExpandedBytes) {
            throw VocabularyPackageException("词包解压数据超过安全上限")
        }
        database.withTransaction {
            val timestamp = now()
            val bookId = database.vocabularyDao().getBookByName(bookName)?.id ?: try {
                database.vocabularyDao().insertBook(
                    VocabularyBookEntity(
                        name = bookName,
                        description = buildDescription(metadata),
                        bookType = VocabularyBookType.IMPORTED,
                        createdOrder = timestamp,
                        createdAtEpochMillis = timestamp,
                        updatedAtEpochMillis = timestamp
                    )
                )
            } catch (_: SQLiteConstraintException) {
                throw VocabularyPackageException("已存在同名词库")
            }
            database.vocabularyDao().upsertSource(SourceRecordEntity(
                id = bookId,
                stableId = metadata.optString("id").ifBlank { "lexipack-source:$bookId" },
                name = bookName,
                description = buildDescription(metadata),
                sourceType = "lexipack",
                license = metadata.optString("license"),
                metadataJson = metadata.toString(),
                createdAtEpochMillis = timestamp,
                updatedAtEpochMillis = timestamp
            ))

            val identities = HashSet<String>(expectedCount)
            var readCount = 0
            var importedCount = 0
            var skippedDuplicateCount = 0
            var updatedCount = 0
            var conflictCount = 0
            val pending = ArrayList<WordEntryEntity>(CHUNK_SIZE)
            zip.getInputStream(entriesEntry).buffered().use { input ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = input.readUtf8LineWithLimit(limits.maxEntryBytes) ?: break
                    if (line.isBlank()) continue
                    if (readCount >= expectedCount) {
                        throw VocabularyPackageException(
                            "词包实际词数超过声明的 $expectedCount 词"
                        )
                    }
                    val parsed = parseEntry(line, bookId, timestamp, readCount)
                    readCount++
                    if (!identities.add(parsed.identityKey())) {
                        throw VocabularyPackageException("词包包含重复词条身份：${parsed.word}")
                    }
                    pending += parsed
                    if (pending.size == CHUNK_SIZE) {
                        val result = insertChunk(pending, timestamp)
                        importedCount += result.inserted
                        updatedCount += result.updated
                        skippedDuplicateCount += result.skipped
                        conflictCount += result.conflicts
                        pending.clear()
                        currentCoroutineContext().ensureActive()
                        onProgress(VocabularyImportProgress(readCount, expectedCount))
                    }
                }
            }
            if (pending.isNotEmpty()) {
                val result = insertChunk(pending, timestamp)
                importedCount += result.inserted
                updatedCount += result.updated
                skippedDuplicateCount += result.skipped
                conflictCount += result.conflicts
                onProgress(VocabularyImportProgress(readCount, expectedCount))
            }
            if (readCount != expectedCount) {
                throw VocabularyPackageException(
                    "词包声明 $expectedCount 词，实际读取 $readCount 词"
                )
            }
            VocabularyImportResult(
                bookId = bookId,
                bookName = bookName,
                entryCount = importedCount,
                skippedDuplicateCount = skippedDuplicateCount,
                updatedCount = updatedCount,
                conflictCount = conflictCount
            )
        }
    }

    private data class ChunkImportResult(
        val inserted: Int,
        val updated: Int,
        val skipped: Int,
        val conflicts: Int
    )

    private suspend fun insertChunk(
        words: List<WordEntryEntity>,
        timestamp: Long
    ): ChunkImportResult {
        val existingWords = database.vocabularyDao()
            .findWords(words.map { it.normalizedWord }).associateBy { it.identityKey() }
        val existingSourceWords = if (existingWords.isEmpty()) emptySet() else {
            database.vocabularyDao().findWordIdsInSource(
                words.first().bookId,
                existingWords.values.map { it.id }
            ).toHashSet()
        }
        var updatedCount = 0
        var skippedCount = 0
        var conflictCount = 0
        words.forEach { incoming ->
            existingWords[incoming.identityKey()]?.let { existing ->
                val merged = com.finisdiscago.leximvp.core.text.ContentMergeService.merge(
                    existing.richContentJson, incoming.richContentJson ?: "{}")
                val updated = existing.copy(
                    meaning = existing.meaning.ifBlank { incoming.meaning },
                    pronunciation = existing.pronunciation?.takeIf { it.isNotBlank() } ?: incoming.pronunciation,
                    richContentJson = merged.json, updatedAtEpochMillis = timestamp
                )
                val contentChanged = updated.meaning != existing.meaning ||
                    updated.pronunciation != existing.pronunciation ||
                    updated.richContentJson != existing.richContentJson
                val sourceAdded = existing.id !in existingSourceWords
                if (contentChanged) database.vocabularyDao().updateWord(updated)
                if (contentChanged || sourceAdded) updatedCount++ else skippedCount++
                conflictCount += merged.conflicts.size
                insertForms(existing.id, incoming)
                insertSource(existing.id, incoming, timestamp)
                insertStructuredContent(existing.id, existing.stableId, incoming)
            }
        }
        val newWords = words.filterNot { it.identityKey() in existingWords }
        if (newWords.isNotEmpty()) {
            val ids = database.vocabularyDao().insertWords(newWords)
            newWords.zip(ids).forEach { (word, id) ->
                insertForms(id, word)
                insertSource(id, word, timestamp)
                insertStructuredContent(id, word.stableId, word)
            }
        }
        // Cards are materialized when the word enters a study plan, not for the whole import.
        return ChunkImportResult(newWords.size, updatedCount, skippedCount, conflictCount)
    }

    private suspend fun insertSource(wordId: Long, word: WordEntryEntity, timestamp: Long) {
        val status = runCatching {
            JSONObject(word.richContentJson ?: "{}").optString("status", "unverified")
        }.getOrDefault("unverified")
        database.vocabularyDao().insertEntrySources(listOf(EntrySourceEntity(
            wordId = wordId,
            sourceId = word.bookId,
            sourceOrder = word.sourceOrder,
            evidenceStatus = status,
            addedAtEpochMillis = timestamp
        )))
    }

    private suspend fun insertForms(wordId: Long, word: WordEntryEntity) {
        val root = runCatching { JSONObject(word.richContentJson ?: "{}") }.getOrNull() ?: return
        val forms = linkedSetOf<String>()
        root.optJSONArray("forms")?.let { array ->
            for (index in 0 until array.length()) {
                val value = array.opt(index)
                when (value) {
                    is String -> forms += value
                    is JSONObject -> value.optString("form").takeIf(String::isNotBlank)?.let(forms::add)
                }
            }
        }
        root.optJSONObject("wordFormation")?.optJSONArray("forms")?.let { array ->
            for (index in 0 until array.length()) array.optJSONObject(index)?.optString("word")?.takeIf(String::isNotBlank)?.let(forms::add)
        }
        val entities = forms.mapNotNull { form ->
            val normalized = WordNormalizer.normalizeHeadword(form)
            if (normalized.isBlank() || normalized == word.normalizedWord) null else WordFormEntity(
                wordId = wordId,
                form = form,
                normalizedForm = normalized,
                relationType = "package_variant",
                source = root.optString("sourceId", "lexipack")
            )
        }
        if (entities.isNotEmpty()) database.vocabularyDao().insertWordForms(entities)
    }

    private suspend fun insertStructuredContent(wordId: Long, wordStableId: String, word: WordEntryEntity) {
        val root = runCatching { JSONObject(word.richContentJson ?: "{}") }.getOrNull() ?: return
        val status = root.optString("status", word.contentStatus)
        val source = root.optString("sourceId", "lexipack")
        val senses = mutableListOf<SenseEntity>()
        val examples = mutableListOf<ExampleEntity>()
        root.optJSONArray("senses")?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val senseId = item.optString("id").ifBlank { "$wordStableId:sense:$index" }
                senses += SenseEntity(
                    stableId = senseId, wordId = wordId, displayOrder = index,
                    partOfSpeech = item.optString("partOfSpeech"), meaningZh = item.optString("meaningZh"),
                    definitionEn = item.optString("definitionEn"),
                    contextsJson = (item.optJSONArray("contexts") ?: JSONArray()).toString(),
                    registerJson = (item.optJSONArray("register") ?: JSONArray()).toString(),
                    semanticBoundary = item.optString("semanticBoundary"), status = status, source = source
                )
                item.optJSONArray("examples")?.let { items ->
                    for (exampleIndex in 0 until items.length()) {
                        val example = items.optJSONObject(exampleIndex) ?: continue
                        val text = example.optString("en").trim()
                        if (text.isEmpty()) continue
                        examples += ExampleEntity(
                            stableId = example.optString("id").ifBlank { "$senseId:example:$exampleIndex" },
                            wordId = wordId, senseId = senseId, displayOrder = exampleIndex,
                            text = text, translation = example.optString("zh"), source = source, status = status
                        )
                    }
                }
            }
        }
        if (senses.isNotEmpty()) database.vocabularyDao().upsertSenses(senses)
        if (examples.isNotEmpty()) database.vocabularyDao().upsertExamples(examples)

        val relations = mutableListOf<EntryRelationEntity>()
        val network = root.optJSONObject("semanticNetwork")
        listOf("antonyms", "hypernyms", "hyponyms").forEach { type ->
            network?.optJSONArray(type)?.let { array ->
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val target = item.optString("word").trim()
                    if (target.isEmpty()) continue
                    val targetId = database.vocabularyDao().getWordByNormalized(WordNormalizer.normalizeHeadword(target))?.id
                    relations += EntryRelationEntity(
                        stableId = "$wordStableId:relation:$type:$index", sourceWordId = wordId,
                        targetWordId = targetId, targetText = target, relationType = type,
                        note = item.optString("note"), status = status, source = source
                    )
                }
            }
        }
        suspend fun addRelations(field: String, fallbackType: String) {
            root.optJSONArray(field)?.let { array ->
                for (index in 0 until array.length()) {
                    val raw = array.opt(index)
                    val item = raw as? JSONObject
                    val target = when (raw) {
                        is String -> raw.trim()
                        is JSONObject -> raw.optString("target").ifBlank { raw.optString("word") }.trim()
                        else -> ""
                    }
                    if (target.isEmpty()) continue
                    val targetStableId = item?.optString("targetId").orEmpty()
                    val targetWord = targetStableId.takeIf(String::isNotBlank)?.let {
                        database.vocabularyDao().getWordByStableId(it)
                    } ?: database.vocabularyDao().getWordByNormalized(WordNormalizer.normalizeHeadword(target))
                    val type = item?.optString("type").orEmpty().ifBlank { fallbackType }
                    relations += EntryRelationEntity(
                        stableId = item?.optString("id").orEmpty().ifBlank { "$wordStableId:relation:$type:$index" },
                        sourceWordId = wordId, targetWordId = targetWord?.id, targetText = target,
                        relationType = type, direction = item?.optString("direction", "outgoing") ?: "outgoing",
                        sourceSenseId = item?.optString("senseId")?.takeIf(String::isNotBlank),
                        note = item?.optString("note").orEmpty(), status = item?.optString("status", status) ?: status,
                        source = item?.optString("source", source) ?: source
                    )
                }
            }
        }
        addRelations("relations", "related")
        addRelations("variants", "spelling_variant")
        addRelations("homographs", "homograph")
        root.optJSONObject("wordFormation")?.optJSONArray("forms")?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val target = item.optString("word").trim()
                if (target.isEmpty()) continue
                relations += EntryRelationEntity(
                    stableId = "$wordStableId:relation:derived_form:$index", sourceWordId = wordId,
                    targetWordId = database.vocabularyDao().getWordByNormalized(WordNormalizer.normalizeHeadword(target))?.id,
                    targetText = target, relationType = "derived_form", note = item.optString("meaningZh"),
                    status = status, source = source
                )
            }
        }
        if (relations.isNotEmpty()) database.vocabularyDao().upsertRelations(relations)

        val tags = linkedMapOf<Pair<String, String>, EntryTagEntity>()
        fun addTag(category: String, value: String) {
            val clean = value.trim()
            if (clean.isEmpty()) return
            tags[category to clean] = EntryTagEntity(wordId, category, clean, word.bookId, status)
        }
        addTag("content_status", word.contentStatus)
        root.optJSONArray("tags")?.let { array ->
            for (index in 0 until array.length()) {
                val value = array.optString(index)
                addTag(if (value.lowercase() in EXAM_TAGS) "exam" else "topic", value)
            }
        }
        listOf("exams" to "exam", "topics" to "topic", "domains" to "domain").forEach { (field, category) ->
            root.optJSONArray(field)?.let { array ->
                for (index in 0 until array.length()) addTag(category, array.optString(index))
            }
        }
        senses.forEach { sense ->
            runCatching { JSONArray(sense.registerJson) }.getOrNull()?.let { array ->
                for (index in 0 until array.length()) addTag("register", array.optString(index))
            }
        }
        if (tags.isNotEmpty()) database.vocabularyDao().upsertEntryTags(tags.values.toList())

        val frequencyItems = root.optJSONArray("frequencyRecords") ?: JSONArray().apply {
            root.optJSONObject("frequencyEvidence")?.let(::put)
        }
        val frequencies = (0 until frequencyItems.length()).mapNotNull { index ->
            val item = frequencyItems.optJSONObject(index) ?: return@mapNotNull null
            val corpusId = item.optString("corpusId").trim()
            val rank = if (item.has("rank") && !item.isNull("rank")) item.optLong("rank").takeIf { it > 0 } else null
            val perMillion = if (item.has("perMillion") && !item.isNull("perMillion")) item.optDouble("perMillion").takeIf { it >= 0.0 } else null
            val band = item.optString("band").trim()
            if (corpusId.isEmpty() || rank == null && perMillion == null && band.isEmpty()) return@mapNotNull null
            FrequencyRecordEntity(
                stableId = item.optString("id").ifBlank { "$wordStableId:frequency:$corpusId" },
                wordId = wordId, sourceId = word.bookId, corpusId = corpusId,
                corpusName = item.optString("corpusName", corpusId),
                corpusVersion = item.optString("corpusVersion"), rank = rank,
                perMillion = perMillion, band = band, license = item.optString("license"),
                evidenceStatus = item.optString("status", status),
                updatedAtEpochMillis = word.updatedAtEpochMillis
            )
        }
        if (frequencies.isNotEmpty()) database.vocabularyDao().upsertFrequencyRecords(frequencies)
    }

    private fun parseEntry(
        rawJson: String,
        bookId: Long,
        timestamp: Long,
        sourceOrder: Int
    ): WordEntryEntity = try {
        parseEntryObject(rawJson, bookId, timestamp, sourceOrder)
    } catch (error: VocabularyPackageException) {
        throw error
    } catch (_: JSONException) {
        throw VocabularyPackageException("词包包含结构无效的词条")
    }

    private fun parseEntryObject(
        rawJson: String,
        bookId: Long,
        timestamp: Long,
        sourceOrder: Int
    ): WordEntryEntity {
        val entry = try {
            JSONObject(rawJson)
        } catch (_: Exception) {
            throw VocabularyPackageException("词包包含无效 JSON")
        }
        val word = entry.requiredString("word")
        val normalizedWord = WordNormalizer.normalizeHeadword(word)
        if (normalizedWord.isEmpty()) throw VocabularyPackageException("词条单词无效：$word")
        val rich = com.finisdiscago.leximvp.core.text.RichWordContentParser.parse(rawJson)
        val meaning = entry.optString("meaning").ifBlank {
            rich.basicMeanings.joinToString("；") { it.partOfSpeech + " " + it.meaningsZh.joinToString("；") }
                .ifBlank { rich.senses.firstOrNull()?.meaningZh.orEmpty() }
        }
        if (entry.optString("language").isBlank()) entry.put("language", "en")
        val homographKey = entry.optString("homographKey").ifBlank { entry.optString("homograph") }
        if (entry.optString("id").isBlank()) entry.put("id", "lexi:${entry.optString("language")}:$normalizedWord${homographKey.takeIf(String::isNotBlank)?.let { ":$it" }.orEmpty()}")
        if (entry.optString("kind").isBlank()) entry.put("kind", if (word.contains(' ')) "phrase" else "word")
        if (entry.optString("status").isBlank()) entry.put("status", if (meaning.isBlank()) "incomplete" else "unverified")
        val firstExample = rich.senses.firstOrNull()?.examples?.firstOrNull()
        return WordEntryEntity(
            bookId = bookId, word = word, normalizedWord = normalizedWord, meaning = meaning,
            concept = rich.coreMeaning.nativeConcept.takeIf { it.isNotBlank() },
            pronunciation = entry.optString("pronunciationText").takeIf(String::isNotBlank)
                ?: listOf(rich.pronunciation.ukIpa, rich.pronunciation.usIpa).filter { it.isNotBlank() }.distinct().joinToString(" / "),
            exampleSentence = firstExample?.en, exampleTranslation = firstExample?.zh,
            tags = entry.optJSONArray("tags")?.let { tags -> (0 until tags.length()).map { tags.optString(it) }.joinToString(",") },
            richContentJson = entry.toString(),
            stableId = entry.optString("id", "lexi:en:$normalizedWord"),
            language = entry.optString("language", "en"),
            entryKind = entry.optString("kind", if (word.contains(' ')) "phrase" else "word"),
            contentStatus = entry.optString("status", if (meaning.isBlank()) "incomplete" else "unverified"),
            homographKey = homographKey,
            sourceOrder = sourceOrder,
            intakeStatus = WordIntakeStatus.PENDING,
            createdAtEpochMillis = timestamp, updatedAtEpochMillis = timestamp
        )
    }

    private fun summarizeMeanings(meanings: JSONArray): String {
        val summaries = ArrayList<String>()
        for (index in 0 until meanings.length()) {
            val meaning = meanings.getJSONObject(index)
            val partOfSpeech = meaning.requiredString("partOfSpeech")
            val translations = meaning.requiredArray("meaningsZh")
            val values = List(translations.length()) { translations.getString(it) }
            summaries += "$partOfSpeech ${values.joinToString("；")}"
        }
        return summaries.joinToString("；")
    }

    private fun summarizeConfusables(confusables: JSONArray): String =
        List(confusables.length()) { index ->
            confusables.getJSONObject(index).requiredString("word")
        }.joinToString(", ")

    private fun buildDescription(metadata: JSONObject): String {
        val source = metadata.optJSONObject("source")
        val sourceName = source?.optString("name").orEmpty()
        val license = source?.optString("license").orEmpty()
        return listOf("Lexi 词包导入", sourceName, license)
            .filter(String::isNotBlank)
            .joinToString(" · ")
    }

    private fun JSONObject.requiredString(name: String): String =
        optString(name).trim().takeIf(String::isNotEmpty)
            ?: throw VocabularyPackageException("字段 $name 不能为空")

    private fun JSONObject.requiredArray(name: String): JSONArray =
        optJSONArray(name) ?: throw VocabularyPackageException("字段 $name 必须是数组")

    private suspend fun InputStream.copyToWithLimit(
        output: java.io.OutputStream,
        maxBytes: Long
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = read(buffer)
            if (read < 0) break
            copied += read
            if (copied > maxBytes) {
                throw VocabularyPackageException(
                    "词包文件超过 ${maxBytes / 1024 / 1024} MB 上限"
                )
            }
            output.write(buffer, 0, read)
        }
    }

    private fun InputStream.readUtf8WithLimit(maxBytes: Int, errorMessage: String): String {
        val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            copied += read
            if (copied > maxBytes) {
                throw VocabularyPackageException(errorMessage)
            }
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private suspend fun InputStream.readUtf8LineWithLimit(maxBytes: Int): String? {
        val output = ByteArrayOutputStream(minOf(maxBytes, 16 * 1024))
        var readAny = false
        while (true) {
            currentCoroutineContext().ensureActive()
            val next = read()
            if (next < 0) {
                return if (readAny) output.toString(Charsets.UTF_8.name()) else null
            }
            readAny = true
            if (next == '\n'.code) {
                return output.toString(Charsets.UTF_8.name()).trimEnd('\r')
            }
            if (output.size() >= maxBytes) {
                throw VocabularyPackageException("词包包含过大的单个词条")
            }
            output.write(next)
        }
    }

    private companion object {
        val EXAM_TAGS = setOf("ielts", "toefl", "gre", "netem", "cet4", "cet6")
        const val SUPPORTED_SCHEMA = 1
        const val CHUNK_SIZE = 100
        const val MANIFEST_FILE = "manifest.json"
        const val ENTRIES_FILE = "entries.jsonl"
        val REQUIRED_SECTIONS = listOf(
            "word",
            "pronunciation",
            "basicMeanings",
            "coreMeaning",
            "etymology",
            "senses",
            "wordFormation",
            "confusables",
            "semanticNetwork",
            "chunks"
        )
    }

    private fun WordEntryEntity.identityKey(): String =
        "$language\u0000$normalizedWord\u0000$homographKey"
}
