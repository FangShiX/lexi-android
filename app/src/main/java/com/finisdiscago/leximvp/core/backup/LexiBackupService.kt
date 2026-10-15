package com.finisdiscago.leximvp.core.backup

import androidx.room.withTransaction
import com.finisdiscago.leximvp.core.database.LexiDatabase
import com.finisdiscago.leximvp.core.database.entity.ArticleEntity
import com.finisdiscago.leximvp.core.database.entity.ArticleTokenEntity
import com.finisdiscago.leximvp.core.database.entity.DailyStudyAssignmentEntity
import com.finisdiscago.leximvp.core.database.entity.LearningAttemptEntity
import com.finisdiscago.leximvp.core.database.entity.ReviewCardEntity
import com.finisdiscago.leximvp.core.database.entity.ReviewStateEntity
import com.finisdiscago.leximvp.core.database.entity.VocabularyBookEntity
import com.finisdiscago.leximvp.core.database.entity.WordEntryEntity
import com.finisdiscago.leximvp.core.database.entity.WordFormEntity
import com.finisdiscago.leximvp.core.database.entity.WordIntakeStatus
import com.finisdiscago.leximvp.core.database.entity.SourceRecordEntity
import com.finisdiscago.leximvp.core.database.entity.EntrySourceEntity
import com.finisdiscago.leximvp.core.database.entity.SenseEntity
import com.finisdiscago.leximvp.core.database.entity.ExampleEntity
import com.finisdiscago.leximvp.core.database.entity.EntryRelationEntity
import com.finisdiscago.leximvp.core.database.entity.EntryTagEntity
import com.finisdiscago.leximvp.core.database.entity.FrequencyRecordEntity
import com.finisdiscago.leximvp.domain.model.UserSettings
import com.finisdiscago.leximvp.domain.repository.SettingsRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.json.JSONArray
import java.io.BufferedInputStream
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class BackupSummary(
    val books: Int,
    val words: Int,
    val attempts: Int,
    val articles: Int
)

class BackupFormatException(message: String) : IllegalArgumentException(message)

class LexiBackupService(
    private val database: LexiDatabase,
    private val settingsRepository: SettingsRepository,
    private val cacheDirectory: File,
    private val filesDirectory: File = cacheDirectory.parentFile ?: cacheDirectory,
    private val appVersion: String,
    private val now: () -> Long = System::currentTimeMillis
) {
    suspend fun export(output: OutputStream): BackupSummary {
        cacheDirectory.mkdirs()
        val temporaryFile = File.createTempFile("lexi-backup-", ".lexibackup", cacheDirectory)
        return try {
            val summary = writeBackup(temporaryFile)
            temporaryFile.inputStream().buffered().use { input ->
                output.buffered().use { target -> input.copyToCancellable(target) }
            }
            summary
        } finally {
            temporaryFile.delete()
        }
    }

    suspend fun restore(input: InputStream): BackupSummary {
        cacheDirectory.mkdirs()
        val temporaryFile = File.createTempFile("lexi-restore-", ".lexibackup", cacheDirectory)
        return try {
            temporaryFile.outputStream().buffered().use { output ->
                input.buffered().use { source ->
                    source.copyToCancellable(output, MAX_BACKUP_BYTES)
                }
            }
            val validated = validateBackup(temporaryFile)
            restoreValidated(temporaryFile, validated.settings, validated.assets)
            validated.summary
        } finally {
            temporaryFile.delete()
        }
    }

    private suspend fun writeBackup(target: File): BackupSummary {
        val settings = settingsRepository.settings.first()
        val dao = database.backupDao()
        val counts = database.withTransaction {
            BackupCounts(
                books = dao.countBooks(),
                words = dao.countWords(),
                forms = dao.countForms(),
                sources = dao.countSources(),
                entrySources = dao.countEntrySources(),
                senses = dao.countSenses(),
                examples = dao.countExamples(),
                relations = dao.countRelations(),
                entryTags = dao.countEntryTags(),
                frequencyRecords = dao.countFrequencyRecords(),
                cards = dao.countCards(),
                states = dao.countStates(),
                attempts = dao.countAttempts(),
                articles = dao.countArticles(),
                tokens = dao.countTokens(),
                assignments = dao.countAssignments(),
                records = database.lexiconDao().allRecords().size
            )
        }
        val assets = portableAssets()
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_FILE))
            zip.write(manifestJson(settings, counts, assets).toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(DATA_FILE))
            val writer = BufferedWriter(OutputStreamWriter(zip, Charsets.UTF_8))
            database.withTransaction {
                writeQuery(writer, "SELECT * FROM workspace_records ORDER BY id", "workspace")
                writeQuery(writer, "SELECT * FROM vocabulary_books ORDER BY id", "book")
                writeQuery(writer, "SELECT * FROM source_records ORDER BY id", "source")
                writeQuery(writer, "SELECT * FROM word_entries ORDER BY id", "word")
                writeQuery(writer, "SELECT * FROM word_forms ORDER BY id", "form")
                writeQuery(writer, "SELECT stableId AS id, * FROM senses ORDER BY wordId, displayOrder", "sense")
                writeQuery(writer, "SELECT stableId AS id, * FROM examples ORDER BY wordId, senseId, displayOrder", "example")
                writeQuery(writer, "SELECT stableId AS id, * FROM entry_relations ORDER BY sourceWordId, relationType, stableId", "relation")
                writeQuery(writer, "SELECT (wordId || ':' || category || ':' || value || ':' || sourceId) AS id, wordId, category, value, sourceId, status FROM entry_tags ORDER BY category, value, wordId", "entryTag")
                writeQuery(writer, "SELECT stableId AS id, * FROM frequency_records ORDER BY corpusId, rank, wordId", "frequency")
                writeQuery(writer, "SELECT (wordId || ':' || sourceId) AS id, wordId, sourceId, sourceOrder, evidenceStatus, addedAtEpochMillis FROM entry_sources ORDER BY sourceId, sourceOrder, wordId", "entrySource")
                writeQuery(
                    writer,
                    "SELECT * FROM daily_study_assignments ORDER BY id",
                    "assignment"
                )
                writeQuery(writer, "SELECT * FROM review_cards ORDER BY id", "card")
                writeQuery(writer, "SELECT * FROM review_states ORDER BY id", "state")
                writeQuery(writer, "SELECT * FROM learning_attempts ORDER BY id", "attempt")
                writeQuery(writer, "SELECT * FROM articles ORDER BY id", "article")
                writeQuery(writer, "SELECT * FROM article_tokens ORDER BY id", "token")
            }
            writer.flush()
            zip.closeEntry()
            assets.forEach { asset ->
                zip.putNextEntry(ZipEntry("$ASSET_PREFIX${asset.path}"))
                asset.file.inputStream().buffered().use { input ->
                    input.copyToCancellable(zip, MAX_ASSET_BYTES)
                }
                zip.closeEntry()
            }
        }
        return counts.toSummary()
    }

    private suspend fun writeQuery(writer: BufferedWriter, sql: String, type: String) {
        database.openHelper.readableDatabase.query(sql).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("id")
            while (cursor.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val record = JSONObject()
                    .put("type", type)
                    .put("id", "$type:${cursor.getString(idIndex)}")
                for (index in 0 until cursor.columnCount) {
                    val name = cursor.getColumnName(index)
                    if (name == "id") continue
                    val referenceType = referenceType(type, name)
                    record.put(
                        name,
                        if (referenceType != null && !cursor.isNull(index)) {
                            "$referenceType:${cursor.getLong(index)}"
                        } else {
                            cursorValue(cursor, index, name)
                        }
                    )
                }
                writer.write(record.toString())
                writer.newLine()
            }
        }
    }

    private fun cursorValue(
        cursor: android.database.Cursor,
        index: Int,
        columnName: String
    ): Any = when {
        cursor.isNull(index) -> JSONObject.NULL
        columnName in BOOLEAN_COLUMNS -> cursor.getInt(index) != 0
        cursor.getType(index) == android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
        cursor.getType(index) == android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
        else -> cursor.getString(index)
    }

    private fun referenceType(recordType: String, columnName: String): String? = when {
        recordType == "word" && columnName == "bookId" -> "book"
        recordType == "form" && columnName == "wordId" -> "word"
        recordType == "sense" && columnName == "wordId" -> "word"
        recordType == "example" && columnName == "wordId" -> "word"
        recordType == "relation" && columnName == "sourceWordId" -> "word"
        recordType == "relation" && columnName == "targetWordId" -> "word"
        recordType == "entryTag" && columnName == "wordId" -> "word"
        recordType == "entryTag" && columnName == "sourceId" -> "source"
        recordType == "frequency" && columnName == "wordId" -> "word"
        recordType == "frequency" && columnName == "sourceId" -> "source"
        recordType == "entrySource" && columnName == "wordId" -> "word"
        recordType == "entrySource" && columnName == "sourceId" -> "source"
        recordType == "assignment" && columnName == "wordId" -> "word"
        recordType == "assignment" && columnName == "bookId" -> "book"
        recordType == "card" && columnName == "wordId" -> "word"
        recordType == "state" && columnName == "cardId" -> "card"
        recordType == "attempt" && columnName == "wordId" -> "word"
        recordType == "attempt" && columnName == "cardId" -> "card"
        recordType == "token" && columnName == "articleId" -> "article"
        recordType == "token" && columnName == "wordId" -> "word"
        else -> null
    }

    private suspend fun validateBackup(file: File): ValidatedBackup = ZipFile(file).use { zip ->
        val manifest = readManifest(zip)
        val schemaVersion = manifest.getInt("schemaVersion")
        val counts = manifest.getJSONObject("counts").toCounts(schemaVersion)
        val settings = manifest.getJSONObject("settings").toSettings()
        val entry = zip.getEntry(DATA_FILE)
            ?: throw BackupFormatException("备份缺少 $DATA_FILE")

        val seen = BackupReferences()
        var dataBytes = 0L
        zip.getInputStream(entry).buffered().use { input ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val line = input.readUtf8Line(MAX_LINE_BYTES) ?: break
                dataBytes += line.toByteArray(Charsets.UTF_8).size + 1
                if (dataBytes > MAX_DATA_BYTES) {
                    throw BackupFormatException("备份展开后数据过大")
                }
                if (line.isBlank()) continue
                seen.accept(parseRecord(line))
            }
        }
        seen.validate(counts)
        val assets = if (schemaVersion >= 4) {
            manifest.optJSONArray("assets").toPortableAssetRecords(zip)
        } else emptyList()
        ValidatedBackup(counts.toSummary(), settings, schemaVersion, assets)
    }

    private fun readManifest(zip: ZipFile): JSONObject {
        val entry = zip.getEntry(MANIFEST_FILE)
            ?: throw BackupFormatException("备份缺少 $MANIFEST_FILE")
        if (entry.size > MAX_MANIFEST_BYTES) {
            throw BackupFormatException("备份清单过大")
        }
        val manifest = try {
            zip.getInputStream(entry).use {
                JSONObject(it.readUtf8(MAX_MANIFEST_BYTES))
            }
        } catch (error: BackupFormatException) {
            throw error
        } catch (_: Exception) {
            throw BackupFormatException("备份清单格式无效")
        }
        if (manifest.optString("format") != FORMAT_NAME) {
            throw BackupFormatException("不是 Lexi 备份文件")
        }
        if (manifest.optInt("schemaVersion") !in MIN_SUPPORTED_SCHEMA..SCHEMA_VERSION) {
            throw BackupFormatException("不支持的备份版本")
        }
        return manifest
    }

    private suspend fun restoreValidated(file: File, settings: UserSettings, assets: List<PortableAssetRecord>) {
        ZipFile(file).use { zip ->
            val staging = File(cacheDirectory, "restore-assets-${now()}").apply { mkdirs() }
            try {
                assets.forEach { asset ->
                    val target = File(staging, asset.path).safeChildOf(staging)
                    target.parentFile?.mkdirs()
                    zip.getInputStream(zip.getEntry("$ASSET_PREFIX${asset.path}")).use { input ->
                        target.outputStream().buffered().use { output -> input.copyToCancellable(output, MAX_ASSET_BYTES) }
                    }
                    if (target.length() != asset.size || target.sha256() != asset.sha256) {
                        throw BackupFormatException("备份附件校验失败：${asset.path}")
                    }
                }
            val entry = zip.getEntry(DATA_FILE)
                ?: throw BackupFormatException("备份缺少 $DATA_FILE")
            database.withTransaction {
                val dao = database.backupDao()
                database.openHelper.writableDatabase.execSQL("DELETE FROM workspace_records")
                dao.deleteTokens()
                dao.deleteArticles()
                dao.deleteAttempts()
                dao.deleteStates()
                dao.deleteCards()
                dao.deleteAssignments()
                dao.deleteEntrySources()
                dao.deleteRelations()
                dao.deleteEntryTags()
                dao.deleteFrequencyRecords()
                dao.deleteExamples()
                dao.deleteSenses()
                dao.deleteForms()
                dao.deleteWords()
                dao.deleteSources()
                dao.deleteBooks()

                val ids = RestoreIds()
                zip.getInputStream(entry).buffered().use { input ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val line = input.readUtf8Line(MAX_LINE_BYTES) ?: break
                        if (line.isBlank()) continue
                        restoreRecord(parseRecord(line), ids)
                    }
                }
                LexiDatabase.ensureSystemReadingBook(
                    database.openHelper.writableDatabase,
                    now()
                )
                LexiDatabase.ensureSourceRecords(database.openHelper.writableDatabase)
            }
                listOf("imports", "media").forEach { File(filesDirectory, it).deleteRecursively() }
                assets.forEach { asset ->
                    val source = File(staging, asset.path).safeChildOf(staging)
                    val target = File(filesDirectory, asset.path).safeChildOf(filesDirectory)
                    target.parentFile?.mkdirs()
                    source.copyTo(target, overwrite = true)
                }
            } finally {
                staging.deleteRecursively()
            }
        }
        settingsRepository.replaceSettings(settings)
    }

    private suspend fun restoreRecord(record: JSONObject, ids: RestoreIds) {
        val dao = database.backupDao()
        val externalId = record.requiredString("id")
        when (record.requiredString("type")) {
            "workspace" -> database.lexiconDao().save(com.finisdiscago.leximvp.core.database.entity.WorkspaceRecordEntity(
                id = externalId.substringAfter(':').toLong(), kind = record.requiredString("kind"),
                title = record.optString("title"), payload = record.requiredString("payload"),
                updatedAtEpochMillis = record.requiredLong("updatedAtEpochMillis")
            ))
            "book" -> ids.books[externalId] = dao.insertBook(
                VocabularyBookEntity(
                    name = record.requiredString("name"),
                    description = record.optString("description"),
                    bookType = record.optString("bookType", "User"),
                    isProtected = record.optBoolean("isProtected", false),
                    selectionEnabled = record.optBoolean("selectionEnabled", true),
                    dailySelectionLimit = record.optInt("dailySelectionLimit", 20),
                    selectionPriority = record.optInt("selectionPriority", 0),
                    createdOrder = record.optLong(
                        "createdOrder",
                        record.requiredLong("createdAtEpochMillis")
                    ),
                    createdAtEpochMillis = record.requiredLong("createdAtEpochMillis"),
                    updatedAtEpochMillis = record.requiredLong("updatedAtEpochMillis")
                )
            )
            "source" -> {
                val legacyBookExternalId = "book:${externalId.substringAfter(':')}"
                val sourceId = ids.books[legacyBookExternalId]
                    ?: externalId.substringAfter(':').toLong()
                ids.sources[externalId] = dao.insertSource(SourceRecordEntity(
                    id = sourceId,
                    stableId = record.requiredString("stableId"),
                    name = record.requiredString("name"),
                    description = record.optString("description"),
                    sourceType = record.optString("sourceType", "legacy_book"),
                    license = record.optString("license"),
                    metadataJson = record.optString("metadataJson", "{}"),
                    createdAtEpochMillis = record.requiredLong("createdAtEpochMillis"),
                    updatedAtEpochMillis = record.requiredLong("updatedAtEpochMillis")
                ))
            }
            "word" -> {
                val normalizedWord = record.requiredString("normalizedWord")
                val language = record.optString("language", "en")
                val homographKey = record.optString("homographKey")
                val stableId = record.optString("stableId").ifBlank {
                    "lexi:$language:$normalizedWord${homographKey.takeIf(String::isNotBlank)?.let { ":$it" }.orEmpty()}"
                }
                ids.words[externalId] = dao.findWordId(stableId, language, normalizedWord, homographKey) ?: dao.insertWord(
                    WordEntryEntity(
                        bookId = ids.books.requireId(record.requiredString("bookId"), "词库"),
                        word = record.requiredString("word"),
                        normalizedWord = normalizedWord,
                        meaning = record.optString("meaning"),
                        concept = record.nullableString("concept"),
                        pronunciation = record.nullableString("pronunciation"),
                        exampleSentence = record.nullableString("exampleSentence"),
                        exampleTranslation = record.nullableString("exampleTranslation"),
                        synonyms = record.nullableString("synonyms"),
                        tags = record.nullableString("tags"),
                        audioUri = record.nullableString("audioUri"),
                        richContentJson = record.nullableString("richContentJson"),
                        stableId = stableId,
                        language = language,
                        entryKind = record.optString(
                            "entryKind",
                            if (record.requiredString("word").contains(' ')) "phrase" else "word"
                        ),
                        contentStatus = record.optString(
                            "contentStatus",
                            if (record.optString("meaning").isBlank()) "incomplete" else "unverified"
                        ),
                        homographKey = homographKey,
                        sourceOrder = record.optInt("sourceOrder"),
                        intakeStatus = record.optString(
                            "intakeStatus",
                            WordIntakeStatus.QUEUED
                        ),
                        queuedAtEpochMillis = record.nullableLong("queuedAtEpochMillis")
                            ?: record.requiredLong("createdAtEpochMillis"),
                        manualFamiliarity = record.nullableString("manualFamiliarity"),
                        proficiencyScore = record.optDouble("proficiencyScore", 0.0),
                        lastSeenAtEpochMillis = record.nullableLong(
                            "lastSeenAtEpochMillis"
                        ),
                        createdAtEpochMillis = record.requiredLong(
                            "createdAtEpochMillis"
                        ),
                        updatedAtEpochMillis = record.requiredLong(
                            "updatedAtEpochMillis"
                        )
                    )
                )
            }
            "form" -> dao.insertForm(WordFormEntity(
                wordId = ids.words.requireId(record.requiredString("wordId"), "词条"),
                form = record.requiredString("form"),
                normalizedForm = record.requiredString("normalizedForm"),
                relationType = record.optString("relationType", "inflection"),
                source = record.optString("source", "backup")
            ))
            "sense" -> dao.insertSense(SenseEntity(
                stableId = externalId.substringAfter(':'),
                wordId = ids.words.requireId(record.requiredString("wordId"), "词条"),
                displayOrder = record.optInt("displayOrder"),
                partOfSpeech = record.optString("partOfSpeech"),
                meaningZh = record.optString("meaningZh"),
                definitionEn = record.optString("definitionEn"),
                contextsJson = record.optString("contextsJson", "[]"),
                registerJson = record.optString("registerJson", "[]"),
                semanticBoundary = record.optString("semanticBoundary"),
                status = record.optString("status", "unverified"),
                source = record.optString("source"), active = record.optBoolean("active", true)
            ))
            "example" -> dao.insertExample(ExampleEntity(
                stableId = externalId.substringAfter(':'),
                wordId = ids.words.requireId(record.requiredString("wordId"), "词条"),
                senseId = record.nullableString("senseId"),
                displayOrder = record.optInt("displayOrder"), text = record.requiredString("text"),
                translation = record.optString("translation"), source = record.optString("source"),
                status = record.optString("status", "unverified"), active = record.optBoolean("active", true)
            ))
            "relation" -> dao.insertRelation(EntryRelationEntity(
                stableId = externalId.substringAfter(':'),
                sourceWordId = ids.words.requireId(record.requiredString("sourceWordId"), "词条"),
                targetWordId = record.nullableString("targetWordId")?.let { ids.words.requireId(it, "关联词条") },
                targetText = record.requiredString("targetText"), relationType = record.requiredString("relationType"),
                direction = record.optString("direction", "outgoing"), sourceSenseId = record.nullableString("sourceSenseId"),
                note = record.optString("note"), status = record.optString("status", "unverified"),
                source = record.optString("source"), active = record.optBoolean("active", true)
            ))
            "entryTag" -> dao.insertEntryTag(EntryTagEntity(
                wordId = ids.words.requireId(record.requiredString("wordId"), "词条"),
                category = record.requiredString("category"), value = record.requiredString("value"),
                sourceId = ids.sources.requireId(record.requiredString("sourceId"), "来源"),
                status = record.optString("status", "unverified")
            ))
            "frequency" -> dao.insertFrequencyRecord(FrequencyRecordEntity(
                stableId = externalId.substringAfter(':'),
                wordId = ids.words.requireId(record.requiredString("wordId"), "词条"),
                sourceId = ids.sources.requireId(record.requiredString("sourceId"), "来源"),
                corpusId = record.requiredString("corpusId"), corpusName = record.requiredString("corpusName"),
                corpusVersion = record.optString("corpusVersion"), rank = record.nullableLong("rank"),
                perMillion = record.nullableDouble("perMillion"), band = record.optString("band"),
                license = record.optString("license"), evidenceStatus = record.optString("evidenceStatus", "unverified"),
                updatedAtEpochMillis = record.requiredLong("updatedAtEpochMillis")
            ))
            "entrySource" -> dao.insertEntrySource(EntrySourceEntity(
                wordId = ids.words.requireId(record.requiredString("wordId"), "词条"),
                sourceId = ids.sources.requireId(record.requiredString("sourceId"), "来源"),
                sourceOrder = record.optInt("sourceOrder"),
                evidenceStatus = record.optString("evidenceStatus", "unverified"),
                addedAtEpochMillis = record.requiredLong("addedAtEpochMillis")
            ))
            "assignment" -> dao.insertAssignment(
                DailyStudyAssignmentEntity(
                    localDate = record.requiredString("localDate"),
                    wordId = ids.words.requireId(record.requiredString("wordId"), "词条"),
                    bookId = ids.books.requireId(record.requiredString("bookId"), "词书"),
                    assignmentKind = record.optString("assignmentKind", "NewWord"),
                    assignedAtEpochMillis = record.requiredLong("assignedAtEpochMillis")
                )
            )
            "card" -> {
                val wordId = ids.words.requireId(record.requiredString("wordId"), "词条")
                val cardType = record.requiredString("cardType")
                val stableId = record.optString("stableId").ifBlank { "legacy-backup:$externalId" }
                ids.cards[externalId] = dao.findCardId(stableId)
                    ?: dao.findLegacyCardId(wordId, cardType)
                    ?: dao.insertCard(
                    ReviewCardEntity(
                        stableId = stableId,
                        wordId = wordId,
                        cardType = cardType,
                        senseId = record.nullableString("senseId"),
                        exampleId = record.nullableString("exampleId"),
                        enabled = record.optBoolean("enabled", true),
                        createdAtEpochMillis = record.requiredLong("createdAtEpochMillis")
                    )
                )
            }
            "state" -> {
                val cardId = ids.cards.requireId(record.requiredString("cardId"), "卡片")
                if (!dao.hasState(cardId)) {
                    dao.insertState(
                        ReviewStateEntity(
                            cardId = cardId,
                            status = record.requiredString("status"),
                            dueAtEpochMillis = record.requiredLong("dueAtEpochMillis"),
                            intervalDays = record.optDouble("intervalDays", 0.0),
                            easeFactor = record.optDouble("easeFactor", 2.5),
                            repetitionCount = record.optInt("repetitionCount"),
                            lapseCount = record.optInt("lapseCount"),
                            lastReviewedAtEpochMillis = record.nullableLong(
                                "lastReviewedAtEpochMillis"
                            )
                        )
                    )
                }
            }
            "attempt" -> dao.insertAttempt(
                LearningAttemptEntity(
                    sessionId = record.requiredString("sessionId"),
                    wordId = ids.words.requireId(record.requiredString("wordId"), "词条"),
                    cardId = record.nullableString("cardId")?.let {
                        ids.cards.requireId(it, "卡片")
                    },
                    rating = record.optInt("rating"),
                    isCorrect = record.nullableBoolean("isCorrect"),
                    responseTimeMillis = record.requiredLong("responseTimeMillis"),
                    attemptKind = record.requiredString("attemptKind"),
                    attemptedAtEpochMillis = record.requiredLong("attemptedAtEpochMillis")
                )
            )
            "article" -> ids.articles[externalId] = dao.insertArticle(
                ArticleEntity(
                    title = record.requiredString("title"),
                    content = record.requiredString("content"),
                    source = record.nullableString("source"),
                    createdAtEpochMillis = record.requiredLong("createdAtEpochMillis"),
                    updatedAtEpochMillis = record.requiredLong("updatedAtEpochMillis"),
                    lastReadOffset = record.optInt("lastReadOffset")
                )
            )
            "token" -> dao.insertToken(
                ArticleTokenEntity(
                    articleId = ids.articles.requireId(
                        record.requiredString("articleId"),
                        "文章"
                    ),
                    rawText = record.requiredString("rawText"),
                    normalizedText = record.requiredString("normalizedText"),
                    startOffset = record.optInt("startOffset"),
                    endOffset = record.optInt("endOffset"),
                    wordId = record.nullableString("wordId")?.let {
                        ids.words.requireId(it, "词条")
                    },
                    learningStatus = record.requiredString("learningStatus")
                )
            )
            else -> throw BackupFormatException("备份包含未知记录类型")
        }
    }

    private fun manifestJson(settings: UserSettings, counts: BackupCounts, assets: List<PortableAsset>) = JSONObject()
        .put("format", FORMAT_NAME)
        .put("schemaVersion", SCHEMA_VERSION)
        .put("appVersion", appVersion)
        .put("exportedAtEpochMillis", now())
        .put("counts", counts.toJson())
        .put("assets", JSONArray().apply {
            assets.forEach { asset -> put(JSONObject().put("path", asset.path).put("size", asset.size).put("sha256", asset.sha256)) }
        })
        .put("settings", settings.toJson())

    private fun portableAssets(): List<PortableAsset> = listOf("imports", "media").flatMap { folder ->
        val root = File(filesDirectory, folder)
        if (!root.exists()) emptyList() else root.walkTopDown().filter(File::isFile).map { file ->
            PortableAsset(file.relativeTo(filesDirectory).invariantSeparatorsPath, file.length(), file.sha256(), file)
        }.toList()
    }

    private fun parseRecord(line: String): JSONObject = try {
        JSONObject(line)
    } catch (_: Exception) {
        throw BackupFormatException("备份包含无效 JSON 记录")
    }

    private suspend fun InputStream.copyToCancellable(
        output: OutputStream,
        maxBytes: Long = Long.MAX_VALUE
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = read(buffer)
            if (read < 0) break
            copied += read
            if (copied > maxBytes) throw BackupFormatException("备份文件过大")
            output.write(buffer, 0, read)
        }
    }

    private fun InputStream.readUtf8(maxBytes: Int): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            copied += read
            if (copied > maxBytes) throw BackupFormatException("备份清单过大")
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private suspend fun BufferedInputStream.readUtf8Line(maxBytes: Int): String? {
        val output = ByteArrayOutputStream(minOf(maxBytes, 16 * 1024))
        var readAny = false
        while (true) {
            currentCoroutineContext().ensureActive()
            val next = read()
            if (next < 0) return if (readAny) output.toString("UTF-8") else null
            readAny = true
            if (next == '\n'.code) return output.toString("UTF-8").trimEnd('\r')
            if (output.size() >= maxBytes) {
                throw BackupFormatException("备份包含过大的单条记录")
            }
            output.write(next)
        }
    }

    private fun JSONArray?.toPortableAssetRecords(zip: ZipFile): List<PortableAssetRecord> {
        if (this == null) return emptyList()
        val seen = mutableSetOf<String>()
        var total = 0L
        return (0 until length()).map { index ->
            val item = optJSONObject(index) ?: throw BackupFormatException("备份附件清单无效")
            val path = item.requiredString("path").replace('\\', '/')
            if (!path.startsWith("imports/") && !path.startsWith("media/")) throw BackupFormatException("备份附件路径无效")
            if (path.contains("../") || path.startsWith('/')) throw BackupFormatException("备份附件路径不安全")
            if (!seen.add(path)) throw BackupFormatException("备份附件重复：$path")
            val size = item.requiredLong("size")
            if (size !in 0..MAX_ASSET_BYTES) throw BackupFormatException("备份附件过大：$path")
            total += size
            if (total > MAX_ASSETS_BYTES) throw BackupFormatException("备份附件总量过大")
            val entry = zip.getEntry("$ASSET_PREFIX$path") ?: throw BackupFormatException("备份缺少附件：$path")
            if (entry.isDirectory) throw BackupFormatException("备份附件无效：$path")
            PortableAssetRecord(path, size, item.requiredString("sha256"))
        }
    }

    private companion object {
        const val FORMAT_NAME = "lexibackup"
        const val MIN_SUPPORTED_SCHEMA = 1
        const val SCHEMA_VERSION = 12
        const val MANIFEST_FILE = "manifest.json"
        const val DATA_FILE = "data.jsonl"
        const val ASSET_PREFIX = "assets/"
        const val MAX_MANIFEST_BYTES = 128 * 1024
        const val MAX_LINE_BYTES = 2 * 1024 * 1024
        const val MAX_BACKUP_BYTES = 1024L * 1024 * 1024
        const val MAX_DATA_BYTES = 4096L * 1024 * 1024
        const val MAX_ASSET_BYTES = 512L * 1024 * 1024
        const val MAX_ASSETS_BYTES = 2048L * 1024 * 1024
        val BOOLEAN_COLUMNS = setOf(
            "enabled",
            "isCorrect",
            "isProtected",
            "selectionEnabled",
            "active"
        )
    }
}

private data class ValidatedBackup(
    val summary: BackupSummary,
    val settings: UserSettings,
    val schemaVersion: Int,
    val assets: List<PortableAssetRecord> = emptyList()
)

private data class PortableAsset(val path: String, val size: Long, val sha256: String, val file: File)
private data class PortableAssetRecord(val path: String, val size: Long, val sha256: String)

private data class BackupCounts(
    val books: Int,
    val words: Int,
    val forms: Int,
    val sources: Int = 0,
    val entrySources: Int = 0,
    val senses: Int = 0,
    val examples: Int = 0,
    val relations: Int = 0,
    val entryTags: Int = 0,
    val frequencyRecords: Int = 0,
    val cards: Int,
    val states: Int,
    val attempts: Int,
    val articles: Int,
    val tokens: Int,
    val assignments: Int,
    val records: Int = 0
) {
    fun toSummary() = BackupSummary(books, words, attempts, articles)

    fun toJson() = JSONObject()
        .put("books", books)
        .put("words", words)
        .put("forms", forms)
        .put("sources", sources)
        .put("entrySources", entrySources)
        .put("senses", senses)
        .put("examples", examples)
        .put("relations", relations)
        .put("entryTags", entryTags)
        .put("frequencyRecords", frequencyRecords)
        .put("cards", cards)
        .put("states", states)
        .put("attempts", attempts)
        .put("articles", articles)
        .put("tokens", tokens)
        .put("assignments", assignments)
        .put("records", records)
}

private class BackupReferences {
    private val ids = mutableMapOf<String, MutableSet<String>>()
    private val references = mutableListOf<Pair<String, String>>()

    fun accept(record: JSONObject) {
        val type = record.requiredString("type")
        val id = record.requiredString("id")
        val typeIds = ids.getOrPut(type) { mutableSetOf() }
        if (!typeIds.add(id)) throw BackupFormatException("备份包含重复记录：$id")
        when (type) {
            "book", "source", "workspace" -> Unit
            "word" -> references += "book" to record.requiredString("bookId")
            "form" -> references += "word" to record.requiredString("wordId")
            "sense" -> references += "word" to record.requiredString("wordId")
            "example" -> references += "word" to record.requiredString("wordId")
            "relation" -> {
                references += "word" to record.requiredString("sourceWordId")
                record.nullableString("targetWordId")?.let { references += "word" to it }
            }
            "entryTag" -> {
                references += "word" to record.requiredString("wordId")
                references += "source" to record.requiredString("sourceId")
            }
            "frequency" -> {
                references += "word" to record.requiredString("wordId")
                references += "source" to record.requiredString("sourceId")
            }
            "entrySource" -> {
                references += "word" to record.requiredString("wordId")
                references += "source" to record.requiredString("sourceId")
            }
            "assignment" -> {
                references += "word" to record.requiredString("wordId")
                references += "book" to record.requiredString("bookId")
            }
            "card" -> references += "word" to record.requiredString("wordId")
            "state" -> references += "card" to record.requiredString("cardId")
            "attempt" -> {
                references += "word" to record.requiredString("wordId")
                record.nullableString("cardId")?.let { references += "card" to it }
            }
            "article" -> Unit
            "token" -> {
                references += "article" to record.requiredString("articleId")
                record.nullableString("wordId")?.let { references += "word" to it }
            }
            else -> throw BackupFormatException("备份包含未知记录类型：$type")
        }
    }

    fun validate(counts: BackupCounts) {
        val expected = mapOf(
            "book" to counts.books,
            "word" to counts.words,
            "form" to counts.forms,
            "source" to counts.sources,
            "entrySource" to counts.entrySources,
            "sense" to counts.senses,
            "example" to counts.examples,
            "relation" to counts.relations,
            "entryTag" to counts.entryTags,
            "frequency" to counts.frequencyRecords,
            "card" to counts.cards,
            "state" to counts.states,
            "attempt" to counts.attempts,
            "article" to counts.articles,
            "token" to counts.tokens,
            "assignment" to counts.assignments,
            "workspace" to counts.records
        )
        expected.forEach { (type, count) ->
            if ((ids[type]?.size ?: 0) != count) {
                throw BackupFormatException("备份中的 $type 数量与清单不一致")
            }
        }
        references.forEach { (type, id) ->
            if (id !in ids[type].orEmpty()) {
                throw BackupFormatException("备份引用了不存在的记录：$id")
            }
        }
    }
}

private class RestoreIds {
    val books = mutableMapOf<String, Long>()
    val sources = mutableMapOf<String, Long>()
    val words = mutableMapOf<String, Long>()
    val cards = mutableMapOf<String, Long>()
    val articles = mutableMapOf<String, Long>()
}

private fun Map<String, Long>.requireId(externalId: String, label: String): Long =
    this[externalId] ?: throw BackupFormatException("$label 引用无效：$externalId")

private fun File.safeChildOf(root: File): File {
    val rootPath = root.canonicalFile.toPath()
    val candidate = canonicalFile
    if (!candidate.toPath().startsWith(rootPath)) throw BackupFormatException("备份附件路径不安全")
    return candidate
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun JSONObject.toCounts(schemaVersion: Int) = BackupCounts(
    books = requiredCount("books"),
    words = requiredCount("words"),
    forms = if (schemaVersion >= 5) requiredCount("forms") else 0,
    sources = if (schemaVersion >= 6) requiredCount("sources") else 0,
    entrySources = if (schemaVersion >= 6) requiredCount("entrySources") else 0,
    senses = if (schemaVersion >= 8) requiredCount("senses") else 0,
    examples = if (schemaVersion >= 8) requiredCount("examples") else 0,
    relations = if (schemaVersion >= 8) requiredCount("relations") else 0,
    entryTags = if (schemaVersion >= 9) requiredCount("entryTags") else 0,
    frequencyRecords = if (schemaVersion >= 10) requiredCount("frequencyRecords") else 0,
    cards = requiredCount("cards"),
    states = requiredCount("states"),
    attempts = requiredCount("attempts"),
    articles = requiredCount("articles"),
    tokens = requiredCount("tokens"),
    assignments = if (schemaVersion >= 2) requiredCount("assignments") else 0,
    records = if (schemaVersion >= 3) requiredCount("records") else 0
)

private fun JSONObject.requiredCount(name: String): Int =
    optInt(name, -1).takeIf { it >= 0 }
        ?: throw BackupFormatException("备份清单中的 $name 数量无效")

private fun UserSettings.toJson() = JSONObject()
    .put("dailyNewWordLimit", dailyNewWordLimit)
    .put("dailyReviewLimit", dailyReviewLimit)
    .put("autoPlayAudio", autoPlayAudio)
    .put("speechRate", speechRate)
    .put("reminderEnabled", reminderEnabled)
    .put("reminderHour", reminderHour)
    .put("reminderMinute", reminderMinute)
    .put("readerFontSizeSp", readerFontSizeSp)
    .put("readerLineHeightSp", readerLineHeightSp)
    .put("themeMode", themeMode.name)

private fun JSONObject.toSettings() = UserSettings(
    dailyNewWordLimit = optInt("dailyNewWordLimit", 20).coerceIn(1, 200),
    dailyReviewLimit = optInt("dailyReviewLimit", 100).coerceIn(1, 1_000),
    autoPlayAudio = optBoolean("autoPlayAudio", true),
    speechRate = optDouble("speechRate", 0.9).toFloat().coerceIn(0.5f, 1.5f),
    reminderEnabled = optBoolean("reminderEnabled", false),
    reminderHour = optInt("reminderHour", 21).coerceIn(0, 23),
    reminderMinute = optInt("reminderMinute", 30).coerceIn(0, 59),
    readerFontSizeSp = optInt("readerFontSizeSp", 19).coerceIn(15, 28),
    readerLineHeightSp = optInt("readerLineHeightSp", 31).coerceIn(22, 44),
    themeMode = com.finisdiscago.leximvp.domain.model.ThemeMode.fromStorage(optString("themeMode"))
)

private fun JSONObject.requiredString(name: String): String =
    optString(name).takeIf(String::isNotBlank)
        ?: throw BackupFormatException("备份字段 $name 无效")

private fun JSONObject.requiredLong(name: String): Long =
    if (has(name) && !isNull(name)) getLong(name)
    else throw BackupFormatException("备份字段 $name 无效")

private fun JSONObject.nullableString(name: String): String? =
    if (!has(name) || isNull(name)) null else getString(name)

private fun JSONObject.nullableLong(name: String): Long? =
    if (!has(name) || isNull(name)) null else getLong(name)

private fun JSONObject.nullableDouble(name: String): Double? =
    if (!has(name) || isNull(name)) null else getDouble(name)

private fun JSONObject.nullableBoolean(name: String): Boolean? =
    if (!has(name) || isNull(name)) null else getBoolean(name)
