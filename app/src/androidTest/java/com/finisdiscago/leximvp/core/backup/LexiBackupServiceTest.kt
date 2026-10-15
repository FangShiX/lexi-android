package com.finisdiscago.leximvp.core.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.finisdiscago.leximvp.core.database.LexiDatabase
import com.finisdiscago.leximvp.core.database.entity.ArticleEntity
import com.finisdiscago.leximvp.core.database.entity.ArticleTokenEntity
import com.finisdiscago.leximvp.core.database.entity.DailyStudyAssignmentEntity
import com.finisdiscago.leximvp.core.database.entity.LearningAttemptEntity
import com.finisdiscago.leximvp.core.database.entity.ReviewCardEntity
import com.finisdiscago.leximvp.core.database.entity.ReviewStateEntity
import com.finisdiscago.leximvp.core.database.entity.VocabularyBookEntity
import com.finisdiscago.leximvp.core.database.entity.WordEntryEntity
import com.finisdiscago.leximvp.core.database.entity.WorkspaceRecordEntity
import com.finisdiscago.leximvp.domain.model.UserSettings
import com.finisdiscago.leximvp.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class LexiBackupServiceTest {
    private lateinit var context: Context
    private lateinit var database: LexiDatabase
    private lateinit var settings: FakeSettingsRepository
    private lateinit var service: LexiBackupService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, LexiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = FakeSettingsRepository()
        service = LexiBackupService(
            database = database,
            settingsRepository = settings,
            cacheDirectory = context.cacheDir,
            appVersion = "test",
            now = { 9_000L }
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun exportAndRestoreRoundTripPreservesAllDataAndSettings() = runTest {
        seedCompleteData()
        val expectedSettings = UserSettings(
            dailyNewWordLimit = 35,
            dailyReviewLimit = 250,
            autoPlayAudio = false,
            speechRate = 1.2f,
            reminderEnabled = true,
            reminderHour = 7,
            reminderMinute = 15,
            readerFontSizeSp = 22,
            readerLineHeightSp = 36,
            themeMode = com.finisdiscago.leximvp.domain.model.ThemeMode.Night
        )
        settings.replaceSettings(expectedSettings)
        val output = ByteArrayOutputStream()

        val exported = service.export(output)
        clearDatabase()
        settings.replaceSettings(UserSettings())
        val restored = service.restore(ByteArrayInputStream(output.toByteArray()))

        val dao = database.backupDao()
        assertEquals(exported, restored)
        assertEquals(2, dao.getBooks().size)
        assertTrue(dao.getBooks().any { it.bookType == "SystemReadingUnknowns" })
        assertEquals(1, dao.getWords().size)
        assertEquals("adapt", dao.getWords().single().word)
        assertEquals("""{"word":"adapt"}""", dao.getWords().single().richContentJson)
        assertEquals("Reviewing", dao.getWords().single().intakeStatus)
        assertEquals(0.6, dao.getWords().single().proficiencyScore, 0.0)
        assertEquals(1, dao.getAssignments().size)
        assertEquals(1, dao.getCards().size)
        assertEquals("Review", dao.getStates().single().status)
        assertEquals(1, dao.getAttempts().size)
        assertEquals(true, dao.getAttempts().single().isCorrect)
        assertEquals(1, dao.getArticles().size)
        assertEquals(1, dao.getTokens().size)
        assertEquals(dao.getWords().single().id, dao.getTokens().single().wordId)
        assertTrue(database.lexiconDao().allRecords().single { it.kind == "module_layout" }.payload.contains("usage-note"))
        assertEquals(expectedSettings, settings.value)
    }

    @Test
    fun invalidBackupDoesNotChangeExistingDatabaseOrSettings() = runTest {
        seedCompleteData()
        val originalSettings = UserSettings(dailyNewWordLimit = 42)
        settings.replaceSettings(originalSettings)
        var failed = false

        try {
            service.restore(ByteArrayInputStream(unsupportedVersionBackup()))
        } catch (_: BackupFormatException) {
            failed = true
        }

        assertTrue(failed)
        assertEquals("adapt", database.backupDao().getWords().single().word)
        assertEquals(1, database.backupDao().getAttempts().size)
        assertEquals(originalSettings, settings.value)
    }

    private suspend fun seedCompleteData() {
        val dao = database.backupDao()
        database.lexiconDao().save(WorkspaceRecordEntity(
            kind = "module_layout",
            title = "adapt",
            payload = """{"order":["senses","usage-note"],"hidden":["etymology"],"collapsed":["usage-note"]}""",
            updatedAtEpochMillis = 900L
        ))
        val bookId = dao.insertBook(
            VocabularyBookEntity(
                name = "测试词库",
                description = "完整备份",
                dailySelectionLimit = 35,
                createdAtEpochMillis = 1_000L,
                updatedAtEpochMillis = 2_000L
            )
        )
        val wordId = dao.insertWord(
            WordEntryEntity(
                stableId = "test:backup:alpha",
                bookId = bookId,
                word = "adapt",
                normalizedWord = "adapt",
                meaning = "适应",
                pronunciation = "/əˈdæpt/",
                richContentJson = """{"word":"adapt"}""",
                intakeStatus = "Reviewing",
                queuedAtEpochMillis = 1_500L,
                proficiencyScore = 0.6,
                createdAtEpochMillis = 1_000L,
                updatedAtEpochMillis = 2_000L
            )
        )
        dao.insertAssignment(
            DailyStudyAssignmentEntity(
                localDate = "2026-06-13",
                wordId = wordId,
                bookId = bookId,
                assignedAtEpochMillis = 3_000L
            )
        )
        val cardId = dao.insertCard(
            ReviewCardEntity(
                stableId = "test:card:foreign-to-meaning",
                wordId = wordId,
                cardType = "ForeignToMeaning",
                createdAtEpochMillis = 1_000L
            )
        )
        dao.insertState(
            ReviewStateEntity(
                cardId = cardId,
                status = "Review",
                dueAtEpochMillis = 5_000L,
                intervalDays = 3.0,
                repetitionCount = 2
            )
        )
        dao.insertAttempt(
            LearningAttemptEntity(
                sessionId = "session-1",
                wordId = wordId,
                cardId = cardId,
                rating = 2,
                isCorrect = true,
                responseTimeMillis = 800L,
                attemptKind = "Review",
                attemptedAtEpochMillis = 4_000L
            )
        )
        val articleId = dao.insertArticle(
            ArticleEntity(
                title = "测试文章",
                content = "We adapt.",
                source = "fixture",
                createdAtEpochMillis = 1_000L,
                updatedAtEpochMillis = 2_000L
            )
        )
        dao.insertToken(
            ArticleTokenEntity(
                articleId = articleId,
                rawText = "adapt",
                normalizedText = "adapt",
                startOffset = 3,
                endOffset = 8,
                wordId = wordId,
                learningStatus = "Learning"
            )
        )
    }

    private suspend fun clearDatabase() {
        val dao = database.backupDao()
        dao.deleteTokens()
        dao.deleteArticles()
        dao.deleteAttempts()
        dao.deleteStates()
        dao.deleteCards()
        dao.deleteAssignments()
        dao.deleteWords()
        dao.deleteBooks()
        assertTrue(dao.getBooks().isEmpty())
        assertNull(dao.getWords().firstOrNull())
    }

    private fun unsupportedVersionBackup(): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(
                JSONObject()
                    .put("format", "lexibackup")
                    .put("schemaVersion", 99)
                    .toString()
                    .toByteArray()
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("data.jsonl"))
            zip.closeEntry()
        }
        return output.toByteArray()
    }
}

private class FakeSettingsRepository : SettingsRepository {
    private val mutableSettings = MutableStateFlow(UserSettings())
    override val settings: Flow<UserSettings> = mutableSettings
    val value: UserSettings get() = mutableSettings.value

    override suspend fun replaceSettings(settings: UserSettings) {
        mutableSettings.value = settings
    }

    override suspend fun setDailyNewWordLimit(value: Int) {
        mutableSettings.value = value.let { mutableSettings.value.copy(dailyNewWordLimit = it) }
    }

    override suspend fun setDailyReviewLimit(value: Int) {
        mutableSettings.value = mutableSettings.value.copy(dailyReviewLimit = value)
    }

    override suspend fun setAutoPlayAudio(value: Boolean) {
        mutableSettings.value = mutableSettings.value.copy(autoPlayAudio = value)
    }

    override suspend fun setSpeechRate(value: Float) {
        mutableSettings.value = mutableSettings.value.copy(speechRate = value)
    }

    override suspend fun setReminderEnabled(value: Boolean) {
        mutableSettings.value = mutableSettings.value.copy(reminderEnabled = value)
    }

    override suspend fun setReminderTime(hour: Int, minute: Int) {
        mutableSettings.value = mutableSettings.value.copy(
            reminderHour = hour,
            reminderMinute = minute
        )
    }
}
