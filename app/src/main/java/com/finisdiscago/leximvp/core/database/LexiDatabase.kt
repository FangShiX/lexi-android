package com.finisdiscago.leximvp.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.finisdiscago.leximvp.core.database.dao.ArticleDao
import com.finisdiscago.leximvp.core.database.dao.BackupDao
import com.finisdiscago.leximvp.core.database.dao.ReviewDao
import com.finisdiscago.leximvp.core.database.dao.StudyPlanDao
import com.finisdiscago.leximvp.core.database.dao.VocabularyDao
import com.finisdiscago.leximvp.core.database.entity.ArticleEntity
import com.finisdiscago.leximvp.core.database.entity.ArticleTokenEntity
import com.finisdiscago.leximvp.core.database.entity.DailyStudyAssignmentEntity
import com.finisdiscago.leximvp.core.database.entity.LearningAttemptEntity
import com.finisdiscago.leximvp.core.database.entity.ReviewCardEntity
import com.finisdiscago.leximvp.core.database.entity.ReviewStateEntity
import com.finisdiscago.leximvp.core.database.entity.SystemVocabularyBook
import com.finisdiscago.leximvp.core.database.entity.VocabularyBookEntity
import com.finisdiscago.leximvp.core.database.entity.VocabularyBookType
import com.finisdiscago.leximvp.core.database.entity.WordEntryEntity
import com.finisdiscago.leximvp.core.database.entity.WordFormEntity
import com.finisdiscago.leximvp.core.database.entity.SourceRecordEntity
import com.finisdiscago.leximvp.core.database.entity.EntrySourceEntity
import com.finisdiscago.leximvp.core.database.entity.SenseEntity
import com.finisdiscago.leximvp.core.database.entity.ExampleEntity
import com.finisdiscago.leximvp.core.database.entity.EntryRelationEntity
import com.finisdiscago.leximvp.core.database.entity.EntryTagEntity
import com.finisdiscago.leximvp.core.database.entity.FrequencyRecordEntity

@Database(
    entities = [
        VocabularyBookEntity::class,
        WordEntryEntity::class,
        WordFormEntity::class,
        SourceRecordEntity::class,
        EntrySourceEntity::class,
        SenseEntity::class,
        ExampleEntity::class,
        EntryRelationEntity::class,
        EntryTagEntity::class,
        FrequencyRecordEntity::class,
        ReviewCardEntity::class,
        ReviewStateEntity::class,
        LearningAttemptEntity::class,
        ArticleEntity::class,
        ArticleTokenEntity::class,
        DailyStudyAssignmentEntity::class,
        com.finisdiscago.leximvp.core.database.entity.WorkspaceRecordEntity::class
    ],
    version = 12,
    exportSchema = true
)
abstract class LexiDatabase : RoomDatabase() {
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun reviewDao(): ReviewDao
    abstract fun articleDao(): ArticleDao
    abstract fun backupDao(): BackupDao
    abstract fun studyPlanDao(): StudyPlanDao

    abstract fun lexiconDao(): com.finisdiscago.leximvp.core.database.dao.LexiconDao

    companion object {
        const val DATABASE_NAME = "lexi.db"

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE review_cards ADD COLUMN stableId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE review_cards ADD COLUMN senseId TEXT")
                database.execSQL("ALTER TABLE review_cards ADD COLUMN exampleId TEXT")
                database.execSQL("UPDATE review_cards SET stableId = (SELECT stableId FROM word_entries WHERE word_entries.id = review_cards.wordId) || ':card:' || cardType")
                database.execSQL("DROP INDEX IF EXISTS index_review_cards_wordId_cardType")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_review_cards_wordId_cardType ON review_cards(wordId, cardType)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_review_cards_stableId ON review_cards(stableId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_review_cards_senseId ON review_cards(senseId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_review_cards_exampleId ON review_cards(exampleId)")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE word_entries ADD COLUMN homographKey TEXT NOT NULL DEFAULT ''")
                database.execSQL("DROP INDEX IF EXISTS index_word_entries_normalizedWord")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_word_entries_normalizedWord ON word_entries(normalizedWord)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_word_entries_language_normalizedWord_homographKey ON word_entries(language, normalizedWord, homographKey)")
                database.execSQL("DROP INDEX IF EXISTS index_word_forms_normalizedForm")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_word_forms_normalizedForm ON word_forms(normalizedForm)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_word_forms_wordId_normalizedForm ON word_forms(wordId, normalizedForm)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS frequency_records (stableId TEXT NOT NULL PRIMARY KEY, wordId INTEGER NOT NULL, sourceId INTEGER NOT NULL, corpusId TEXT NOT NULL, corpusName TEXT NOT NULL, corpusVersion TEXT NOT NULL, rank INTEGER, perMillion REAL, band TEXT NOT NULL, license TEXT NOT NULL, evidenceStatus TEXT NOT NULL, updatedAtEpochMillis INTEGER NOT NULL, FOREIGN KEY(wordId) REFERENCES word_entries(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(sourceId) REFERENCES source_records(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_frequency_records_wordId ON frequency_records(wordId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_frequency_records_sourceId ON frequency_records(sourceId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_frequency_records_corpusId_rank ON frequency_records(corpusId, rank)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_frequency_records_corpusId_perMillion ON frequency_records(corpusId, perMillion)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS entry_tags (wordId INTEGER NOT NULL, category TEXT NOT NULL, value TEXT NOT NULL, sourceId INTEGER NOT NULL, status TEXT NOT NULL, PRIMARY KEY(wordId, category, value, sourceId), FOREIGN KEY(wordId) REFERENCES word_entries(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(sourceId) REFERENCES source_records(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_entry_tags_wordId ON entry_tags(wordId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_entry_tags_sourceId ON entry_tags(sourceId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_entry_tags_category_value ON entry_tags(category, value)")
                database.execSQL("INSERT OR IGNORE INTO entry_tags SELECT id, 'content_status', contentStatus, bookId, contentStatus FROM word_entries")
                database.execSQL(
                    """
                    INSERT OR IGNORE INTO entry_tags
                    SELECT w.id,
                           CASE WHEN lower(CAST(t.value AS TEXT)) IN ('ielts','toefl','gre','netem','cet4','cet6') THEN 'exam' ELSE 'topic' END,
                           CAST(t.value AS TEXT), w.bookId, w.contentStatus
                    FROM word_entries w,
                         json_each(CASE WHEN json_valid(w.richContentJson) THEN w.richContentJson ELSE '{}' END, '$.tags') t
                    WHERE trim(CAST(t.value AS TEXT)) != ''
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    INSERT OR IGNORE INTO entry_tags
                    SELECT s.wordId, 'register', CAST(r.value AS TEXT), w.bookId, s.status
                    FROM senses s JOIN word_entries w ON w.id = s.wordId,
                         json_each(CASE WHEN json_valid(s.registerJson) THEN s.registerJson ELSE '[]' END) r
                    WHERE trim(CAST(r.value AS TEXT)) != ''
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS senses (stableId TEXT NOT NULL PRIMARY KEY, wordId INTEGER NOT NULL, displayOrder INTEGER NOT NULL, partOfSpeech TEXT NOT NULL, meaningZh TEXT NOT NULL, definitionEn TEXT NOT NULL, contextsJson TEXT NOT NULL, registerJson TEXT NOT NULL, semanticBoundary TEXT NOT NULL, status TEXT NOT NULL, source TEXT NOT NULL, active INTEGER NOT NULL, FOREIGN KEY(wordId) REFERENCES word_entries(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_senses_wordId ON senses(wordId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_senses_wordId_displayOrder ON senses(wordId, displayOrder)")
                database.execSQL("CREATE TABLE IF NOT EXISTS examples (stableId TEXT NOT NULL PRIMARY KEY, wordId INTEGER NOT NULL, senseId TEXT, displayOrder INTEGER NOT NULL, text TEXT NOT NULL, translation TEXT NOT NULL, source TEXT NOT NULL, status TEXT NOT NULL, active INTEGER NOT NULL, FOREIGN KEY(wordId) REFERENCES word_entries(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_examples_wordId ON examples(wordId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_examples_senseId ON examples(senseId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_examples_senseId_displayOrder ON examples(senseId, displayOrder)")
                database.execSQL("CREATE TABLE IF NOT EXISTS entry_relations (stableId TEXT NOT NULL PRIMARY KEY, sourceWordId INTEGER NOT NULL, targetWordId INTEGER, targetText TEXT NOT NULL, relationType TEXT NOT NULL, direction TEXT NOT NULL, sourceSenseId TEXT, note TEXT NOT NULL, status TEXT NOT NULL, source TEXT NOT NULL, active INTEGER NOT NULL, FOREIGN KEY(sourceWordId) REFERENCES word_entries(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(targetWordId) REFERENCES word_entries(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_entry_relations_sourceWordId ON entry_relations(sourceWordId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_entry_relations_targetWordId ON entry_relations(targetWordId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_entry_relations_relationType ON entry_relations(relationType)")
                backfillStructuredContent(database)
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE word_entries ADD COLUMN stableId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE word_entries ADD COLUMN language TEXT NOT NULL DEFAULT 'en'")
                database.execSQL("ALTER TABLE word_entries ADD COLUMN entryKind TEXT NOT NULL DEFAULT 'word'")
                database.execSQL("ALTER TABLE word_entries ADD COLUMN contentStatus TEXT NOT NULL DEFAULT 'unverified'")
                // Android 8 SQLite does not include JSON1. Parse legacy payloads
                // in Kotlin and stream the updates without losing any word rows.
                val usedIds = mutableSetOf<String>()
                database.compileStatement("UPDATE word_entries SET stableId = ? WHERE id = ?").use { update ->
                    database.query("SELECT id, normalizedWord, richContentJson FROM word_entries ORDER BY id").use { rows ->
                        while (rows.moveToNext()) {
                            val id = rows.getLong(0)
                            val fallback = "lexi:en:${rows.getString(1)}"
                            val fromPayload = if (rows.isNull(2)) null else runCatching {
                                org.json.JSONObject(rows.getString(2)).optString("id").takeIf { it.trim(' ').isNotEmpty() }
                            }.getOrNull()
                            var stableId = fromPayload ?: fallback
                            if (stableId in usedIds) stableId = "$fallback:$id"
                            while (!usedIds.add(stableId)) stableId += ":$id"
                            update.clearBindings()
                            update.bindString(1, stableId)
                            update.bindLong(2, id)
                            update.executeUpdateDelete()
                        }
                    }
                }
                database.execSQL("UPDATE word_entries SET entryKind = 'phrase' WHERE instr(trim(word), ' ') > 0")
                database.execSQL("UPDATE word_entries SET contentStatus = 'incomplete' WHERE trim(meaning) = ''")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_word_entries_stableId ON word_entries(stableId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_word_entries_language_entryKind ON word_entries(language, entryKind)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_word_entries_contentStatus ON word_entries(contentStatus)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS source_records (
                        id INTEGER NOT NULL PRIMARY KEY,
                        stableId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL,
                        sourceType TEXT NOT NULL,
                        license TEXT NOT NULL,
                        metadataJson TEXT NOT NULL,
                        createdAtEpochMillis INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_source_records_stableId ON source_records(stableId)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS entry_sources (
                        wordId INTEGER NOT NULL,
                        sourceId INTEGER NOT NULL,
                        sourceOrder INTEGER NOT NULL,
                        evidenceStatus TEXT NOT NULL,
                        addedAtEpochMillis INTEGER NOT NULL,
                        PRIMARY KEY(wordId, sourceId),
                        FOREIGN KEY(wordId) REFERENCES word_entries(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(sourceId) REFERENCES source_records(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_entry_sources_wordId ON entry_sources(wordId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_entry_sources_sourceId ON entry_sources(sourceId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_entry_sources_sourceId_sourceOrder ON entry_sources(sourceId, sourceOrder)")
                ensureSourceRecords(database)
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS word_forms (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        wordId INTEGER NOT NULL,
                        form TEXT NOT NULL,
                        normalizedForm TEXT NOT NULL,
                        relationType TEXT NOT NULL,
                        source TEXT NOT NULL,
                        FOREIGN KEY(wordId) REFERENCES word_entries(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_word_forms_wordId ON word_forms(wordId)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_word_forms_normalizedForm ON word_forms(normalizedForm)")
            }
        }

        @Volatile
        private var instance: LexiDatabase? = null

        fun getInstance(context: Context): LexiDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LexiDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                    .addCallback(
                        object : Callback() {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                ensureSystemReadingBook(db, System.currentTimeMillis())
                            }
                        }
                    )
                    .build()
                    .also { instance = it }
            }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS workspace_records (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, kind TEXT NOT NULL, title TEXT NOT NULL, payload TEXT NOT NULL, updatedAtEpochMillis INTEGER NOT NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_workspace_records_kind ON workspace_records(kind)")
                // Existing book IDs remain source identities; no word/card/history is deleted.
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE word_entries ADD COLUMN richContentJson TEXT"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE vocabulary_books ADD COLUMN bookType TEXT NOT NULL DEFAULT '${VocabularyBookType.USER}'"
                )
                database.execSQL(
                    "ALTER TABLE vocabulary_books ADD COLUMN isProtected INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE vocabulary_books ADD COLUMN selectionEnabled INTEGER NOT NULL DEFAULT 1"
                )
                database.execSQL(
                    "ALTER TABLE vocabulary_books ADD COLUMN dailySelectionLimit INTEGER NOT NULL DEFAULT 20"
                )
                database.execSQL(
                    "ALTER TABLE vocabulary_books ADD COLUMN selectionPriority INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE vocabulary_books ADD COLUMN createdOrder INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    """
                    UPDATE vocabulary_books
                    SET bookType = '${VocabularyBookType.IMPORTED}'
                    WHERE description LIKE 'Lexi %'
                    """.trimIndent()
                )
                backfillBookOrder(database)

                database.execSQL(
                    "ALTER TABLE word_entries ADD COLUMN sourceOrder INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE word_entries ADD COLUMN intakeStatus TEXT NOT NULL DEFAULT 'Queued'"
                )
                database.execSQL(
                    "ALTER TABLE word_entries ADD COLUMN queuedAtEpochMillis INTEGER"
                )
                database.execSQL(
                    "ALTER TABLE word_entries ADD COLUMN manualFamiliarity TEXT"
                )
                database.execSQL(
                    "ALTER TABLE word_entries ADD COLUMN proficiencyScore REAL NOT NULL DEFAULT 0.0"
                )
                database.execSQL(
                    "ALTER TABLE word_entries ADD COLUMN lastSeenAtEpochMillis INTEGER"
                )
                backfillWordOrder(database)
                mergeDuplicateWords(database)
                backfillLearningMetadata(database)

                database.execSQL("DROP INDEX IF EXISTS index_word_entries_bookId_normalizedWord")
                database.execSQL("DROP INDEX IF EXISTS index_word_entries_normalizedWord")
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_word_entries_normalizedWord ON word_entries(normalizedWord)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_word_entries_bookId_sourceOrder ON word_entries(bookId, sourceOrder)"
                )

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_study_assignments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        localDate TEXT NOT NULL,
                        wordId INTEGER NOT NULL,
                        bookId INTEGER NOT NULL,
                        assignmentKind TEXT NOT NULL,
                        assignedAtEpochMillis INTEGER NOT NULL,
                        FOREIGN KEY(wordId) REFERENCES word_entries(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(bookId) REFERENCES vocabulary_books(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_daily_study_assignments_wordId ON daily_study_assignments(wordId)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_daily_study_assignments_bookId ON daily_study_assignments(bookId)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_daily_study_assignments_localDate ON daily_study_assignments(localDate)"
                )
                database.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS
                        index_daily_study_assignments_localDate_wordId
                    ON daily_study_assignments(localDate, wordId)
                    """.trimIndent()
                )

                ensureSystemReadingBook(database, System.currentTimeMillis())
            }
        }

        private fun backfillBookOrder(database: SupportSQLiteDatabase) {
            database.query(
                "SELECT id FROM vocabulary_books ORDER BY createdAtEpochMillis, id"
            ).use { cursor ->
                var order = 0L
                while (cursor.moveToNext()) {
                    database.execSQL(
                        "UPDATE vocabulary_books SET createdOrder = ? WHERE id = ?",
                        arrayOf(order++, cursor.getLong(0))
                    )
                }
            }
        }

        private fun backfillWordOrder(database: SupportSQLiteDatabase) {
            database.query(
                "SELECT id, bookId FROM word_entries ORDER BY bookId, id"
            ).use { cursor ->
                var currentBookId = Long.MIN_VALUE
                var order = 0
                while (cursor.moveToNext()) {
                    val wordId = cursor.getLong(0)
                    val bookId = cursor.getLong(1)
                    if (bookId != currentBookId) {
                        currentBookId = bookId
                        order = 0
                    }
                    database.execSQL(
                        "UPDATE word_entries SET sourceOrder = ? WHERE id = ?",
                        arrayOf(order++, wordId)
                    )
                }
            }
        }

        private fun mergeDuplicateWords(database: SupportSQLiteDatabase) {
            database.query(
                """
                SELECT w.id, w.normalizedWord
                FROM word_entries w
                JOIN vocabulary_books b ON b.id = w.bookId
                ORDER BY w.normalizedWord, b.createdAtEpochMillis, b.id,
                    w.createdAtEpochMillis, w.id
                """.trimIndent()
            ).use { cursor ->
                var normalizedWord: String? = null
                var survivorId = 0L
                while (cursor.moveToNext()) {
                    val wordId = cursor.getLong(0)
                    val candidate = cursor.getString(1)
                    if (candidate != normalizedWord) {
                        normalizedWord = candidate
                        survivorId = wordId
                    } else {
                        database.execSQL(
                            "UPDATE article_tokens SET wordId = ? WHERE wordId = ?",
                            arrayOf(survivorId, wordId)
                        )
                        database.execSQL(
                            """
                            UPDATE learning_attempts
                            SET cardId = NULL, wordId = ?
                            WHERE wordId = ?
                            """.trimIndent(),
                            arrayOf(survivorId, wordId)
                        )
                        database.execSQL(
                            "DELETE FROM word_entries WHERE id = ?",
                            arrayOf(wordId)
                        )
                    }
                }
            }
        }

        private fun backfillLearningMetadata(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                UPDATE word_entries
                SET intakeStatus = CASE
                    WHEN EXISTS (
                        SELECT 1 FROM review_cards c
                        JOIN review_states s ON s.cardId = c.id
                        WHERE c.wordId = word_entries.id
                          AND s.status IN ('Learning', 'Relearning')
                    ) THEN 'Learning'
                    WHEN EXISTS (
                        SELECT 1 FROM review_cards c
                        JOIN review_states s ON s.cardId = c.id
                        WHERE c.wordId = word_entries.id AND s.status = 'Review'
                    ) THEN 'Reviewing'
                    WHEN EXISTS (
                        SELECT 1 FROM review_cards c
                        JOIN review_states s ON s.cardId = c.id
                        WHERE c.wordId = word_entries.id AND s.status = 'Mastered'
                    ) THEN 'Mature'
                    ELSE 'Queued'
                END,
                proficiencyScore = CASE
                    WHEN EXISTS (
                        SELECT 1 FROM review_cards c
                        JOIN review_states s ON s.cardId = c.id
                        WHERE c.wordId = word_entries.id AND s.status = 'Mastered'
                    ) THEN 1.0
                    WHEN EXISTS (
                        SELECT 1 FROM review_cards c
                        JOIN review_states s ON s.cardId = c.id
                        WHERE c.wordId = word_entries.id AND s.status = 'Review'
                    ) THEN 0.6
                    WHEN EXISTS (
                        SELECT 1 FROM review_cards c
                        JOIN review_states s ON s.cardId = c.id
                        WHERE c.wordId = word_entries.id
                          AND s.status IN ('Learning', 'Relearning')
                    ) THEN 0.25
                    ELSE 0.0
                END,
                queuedAtEpochMillis = createdAtEpochMillis,
                lastSeenAtEpochMillis = (
                    SELECT MAX(a.attemptedAtEpochMillis)
                    FROM learning_attempts a
                    WHERE a.wordId = word_entries.id
                )
                """.trimIndent()
            )
        }

        internal fun ensureSystemReadingBook(
            database: SupportSQLiteDatabase,
            timestamp: Long
        ) {
            database.execSQL(
                """
                UPDATE vocabulary_books
                SET bookType = '${VocabularyBookType.SYSTEM_READING_UNKNOWNS}',
                    isProtected = 1,
                    selectionEnabled = 1
                WHERE id = (
                    SELECT id FROM vocabulary_books
                    WHERE name = '${SystemVocabularyBook.READING_UNKNOWNS_NAME}'
                    ORDER BY createdAtEpochMillis, id
                    LIMIT 1
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT INTO vocabulary_books (
                    name, description, bookType, isProtected, selectionEnabled,
                    dailySelectionLimit, selectionPriority, createdOrder,
                    createdAtEpochMillis, updatedAtEpochMillis
                )
                SELECT ?, '', ?, 1, 1, 20, 100, ?, ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM vocabulary_books WHERE bookType = ?
                )
                """.trimIndent(),
                arrayOf(
                    SystemVocabularyBook.READING_UNKNOWNS_NAME,
                    VocabularyBookType.SYSTEM_READING_UNKNOWNS,
                    Long.MAX_VALUE,
                    timestamp,
                    timestamp,
                    VocabularyBookType.SYSTEM_READING_UNKNOWNS
                )
            )
            val hasSourceRecords = database.query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'source_records'").use { it.moveToFirst() }
            if (hasSourceRecords) database.execSQL(
                """
                INSERT OR IGNORE INTO source_records (
                    id, stableId, name, description, sourceType, license, metadataJson,
                    createdAtEpochMillis, updatedAtEpochMillis
                )
                SELECT id, 'system:reading-unknowns', name, description, 'reading', '', '{}',
                       createdAtEpochMillis, updatedAtEpochMillis
                FROM vocabulary_books
                WHERE bookType = ?
                LIMIT 1
                """.trimIndent(),
                arrayOf(VocabularyBookType.SYSTEM_READING_UNKNOWNS)
            )
        }

        internal fun ensureSourceRecords(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                INSERT OR IGNORE INTO source_records (
                    id, stableId, name, description, sourceType, license, metadataJson,
                    createdAtEpochMillis, updatedAtEpochMillis
                )
                SELECT id, 'legacy-book:' || id, name, description,
                       CASE WHEN bookType = '${VocabularyBookType.SYSTEM_READING_UNKNOWNS}' THEN 'reading' ELSE 'legacy_book' END,
                       '', '{}', createdAtEpochMillis, updatedAtEpochMillis
                FROM vocabulary_books
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT OR IGNORE INTO entry_sources (
                    wordId, sourceId, sourceOrder, evidenceStatus, addedAtEpochMillis
                )
                SELECT id, bookId, sourceOrder, 'unverified', createdAtEpochMillis
                FROM word_entries
                """.trimIndent()
            )
        }

        private fun backfillStructuredContent(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                INSERT OR IGNORE INTO senses
                SELECT w.stableId || ':sense:' || s.key, w.id, CAST(s.key AS INTEGER),
                       COALESCE(json_extract(s.value, '$.partOfSpeech'), ''),
                       COALESCE(json_extract(s.value, '$.meaningZh'), ''),
                       COALESCE(json_extract(s.value, '$.definitionEn'), ''),
                       COALESCE(json_extract(s.value, '$.contexts'), '[]'),
                       COALESCE(json_extract(s.value, '$.register'), '[]'),
                       COALESCE(json_extract(s.value, '$.semanticBoundary'), ''),
                       w.contentStatus, '', 1
                FROM word_entries w,
                     json_each(CASE WHEN json_valid(w.richContentJson) THEN w.richContentJson ELSE '{}' END, '$.senses') s
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT OR IGNORE INTO examples
                SELECT w.stableId || ':sense:' || s.key || ':example:' || e.key,
                       w.id, w.stableId || ':sense:' || s.key, CAST(e.key AS INTEGER),
                       COALESCE(json_extract(e.value, '$.en'), ''),
                       COALESCE(json_extract(e.value, '$.zh'), ''), '', w.contentStatus, 1
                FROM word_entries w,
                     json_each(CASE WHEN json_valid(w.richContentJson) THEN w.richContentJson ELSE '{}' END, '$.senses') s,
                     json_each(s.value, '$.examples') e
                WHERE trim(COALESCE(json_extract(e.value, '$.en'), '')) != ''
                """.trimIndent()
            )
            listOf("antonyms", "hypernyms", "hyponyms").forEach { relation ->
                database.execSQL(
                    """
                    INSERT OR IGNORE INTO entry_relations
                    SELECT w.stableId || ':relation:$relation:' || r.key, w.id,
                           (SELECT id FROM word_entries t WHERE t.normalizedWord = lower(json_extract(r.value, '$.word')) LIMIT 1),
                           COALESCE(json_extract(r.value, '$.word'), ''), '$relation', 'outgoing', NULL,
                           COALESCE(json_extract(r.value, '$.note'), ''), w.contentStatus, '', 1
                    FROM word_entries w,
                         json_each(CASE WHEN json_valid(w.richContentJson) THEN w.richContentJson ELSE '{}' END, '$.semanticNetwork.$relation') r
                    WHERE trim(COALESCE(json_extract(r.value, '$.word'), '')) != ''
                    """.trimIndent()
                )
            }
        }
    }
}
