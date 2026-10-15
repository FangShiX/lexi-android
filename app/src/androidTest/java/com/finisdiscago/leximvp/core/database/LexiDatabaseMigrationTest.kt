package com.finisdiscago.leximvp.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.room.migration.Migration
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LexiDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LexiDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migrateThreeToFourPreservesSchemaAndAddsWorkspaceRecords() {
        helper.createDatabase(TEST_DATABASE, 3).close()
        helper.runMigrationsAndValidate(TEST_DATABASE, 4, true, LexiDatabase.MIGRATION_3_4).close()
    }

    @Test
    fun migrateOneToTwoPreservesExistingWords() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """
                INSERT INTO vocabulary_books
                    (id, name, description, createdAtEpochMillis, updatedAtEpochMillis)
                VALUES (1, '旧词库', '迁移测试', 1000, 1000)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO word_entries
                    (
                        id, bookId, word, normalizedWord, meaning, concept,
                        pronunciation, exampleSentence, exampleTranslation,
                        synonyms, tags, audioUri, createdAtEpochMillis, updatedAtEpochMillis
                    )
                VALUES
                    (
                        1, 1, 'adapt', 'adapt', '适应', NULL,
                        '/əˈdæpt/', 'We adapt.', '我们适应。',
                        NULL, NULL, NULL, 1000, 1000
                    )
                """.trimIndent()
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            2,
            true,
            LexiDatabase.MIGRATION_1_2
        )
        try {
            val cursor = migrated.query(
                "SELECT word, meaning, richContentJson FROM word_entries WHERE id = 1"
            )
            cursor.use {
                it.moveToFirst()
                assertEquals("adapt", it.getString(0))
                assertEquals("适应", it.getString(1))
                assertNull(it.getString(2))
            }
        } finally {
            migrated.close()
        }
    }

    @Test
    fun migrateTwoToThreeKeepsOldestWordAndCreatesSystemBook() {
        helper.createDatabase(TEST_DATABASE, 2).apply {
            execSQL(
                """
                INSERT INTO vocabulary_books
                    (id, name, description, createdAtEpochMillis, updatedAtEpochMillis)
                VALUES
                    (1, 'Older', '', 1000, 1000),
                    (2, 'Newer', '', 2000, 2000)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO word_entries (
                    id, bookId, word, normalizedWord, meaning, richContentJson,
                    createdAtEpochMillis, updatedAtEpochMillis
                )
                VALUES
                    (1, 1, 'Adapt', 'adapt', 'old', NULL, 1000, 1000),
                    (2, 2, 'adapt', 'adapt', 'new', NULL, 2000, 2000)
                """.trimIndent()
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            3,
            true,
            LexiDatabase.MIGRATION_2_3
        )

        migrated.query(
            "SELECT id, bookId, sourceOrder, intakeStatus FROM word_entries"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
            assertEquals(1L, cursor.getLong(1))
            assertEquals(0, cursor.getInt(2))
            assertEquals("Queued", cursor.getString(3))
            assertTrue(!cursor.moveToNext())
        }
        migrated.query(
            """
            SELECT isProtected FROM vocabulary_books
            WHERE bookType = 'SystemReadingUnknowns'
            """.trimIndent()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
    }

    @Test
    fun everyHistoricalSchemaMigratesToCurrentVersion() {
        val migrations: List<Migration> = listOf(
            LexiDatabase.MIGRATION_1_2,
            LexiDatabase.MIGRATION_2_3,
            LexiDatabase.MIGRATION_3_4,
            LexiDatabase.MIGRATION_4_5,
            LexiDatabase.MIGRATION_5_6,
            LexiDatabase.MIGRATION_6_7,
            LexiDatabase.MIGRATION_7_8,
            LexiDatabase.MIGRATION_8_9,
            LexiDatabase.MIGRATION_9_10,
            LexiDatabase.MIGRATION_10_11,
            LexiDatabase.MIGRATION_11_12
        )
        (1 until 12).forEach { startVersion ->
            val databaseName = "$TEST_DATABASE-$startVersion"
            context.deleteDatabase(databaseName)
            helper.createDatabase(databaseName, startVersion).close()
            helper.runMigrationsAndValidate(
                databaseName,
                12,
                true,
                *migrations.drop(startVersion - 1).toTypedArray()
            ).close()
            context.deleteDatabase(databaseName)
        }
    }

    private companion object {
        const val TEST_DATABASE = "lexi-migration-test"
    }
}
