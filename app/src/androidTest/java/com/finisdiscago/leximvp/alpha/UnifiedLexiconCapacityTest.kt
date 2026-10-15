package com.finisdiscago.leximvp.alpha

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.finisdiscago.leximvp.core.database.LexiDatabase
import com.finisdiscago.leximvp.core.database.entity.VocabularyBookEntity
import com.finisdiscago.leximvp.core.backup.LexiBackupService
import com.finisdiscago.leximvp.domain.model.UserSettings
import com.finisdiscago.leximvp.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UnifiedLexiconCapacityTest {
    @Test fun twoHundredThousandEntriesRemainPagedAndSearchable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LexiDatabase::class.java).build()
        try {
            val book = db.vocabularyDao().insertBook(VocabularyBookEntity(name = "capacity", createdAtEpochMillis = 1, updatedAtEpochMillis = 1))
            val started = System.currentTimeMillis()
            db.runInTransaction {
                val statement = db.openHelper.writableDatabase.compileStatement("""
                    INSERT INTO word_entries
                    (bookId,word,normalizedWord,meaning,stableId,language,entryKind,contentStatus,homographKey,sourceOrder,intakeStatus,proficiencyScore,createdAtEpochMillis,updatedAtEpochMillis)
                    VALUES (?,?,?,?,?,'en','word','incomplete','',?,'Pending',0,1,1)
                """.trimIndent())
                repeat(200_000) { index ->
                    val value = "word%06d".format(index)
                    statement.clearBindings(); statement.bindLong(1, book); statement.bindString(2, value)
                    statement.bindString(3, value); statement.bindString(4, "definition $index"); statement.bindString(5, "capacity:$value"); statement.bindLong(6, index.toLong())
                    statement.executeInsert()
                }
            }
            assertEquals(200_000, db.lexiconDao().count().first())
            assertTrue("200k seed exceeded 150 seconds", System.currentTimeMillis() - started < 150_000)
            val pageStarted = System.currentTimeMillis()
            val page = db.lexiconDao().page("word199", "", 0, "", "", "alphabet", 50, 0).first()
            assertEquals(50, page.size)
            assertTrue("prefix page query exceeded 2 seconds", System.currentTimeMillis() - pageStarted < 2_000)
            db.openHelper.writableDatabase.execSQL("INSERT INTO entry_relations (stableId,sourceWordId,targetWordId,targetText,relationType,direction,note,status,source,active) VALUES ('capacity:relation',1,2,'word000001','related','outgoing','','verified','capacity',1)")
            val relationStarted = System.currentTimeMillis()
            db.openHelper.readableDatabase.query("SELECT targetText FROM entry_relations WHERE sourceWordId = 1").use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals("word000001", cursor.getString(0))
            }
            assertTrue("relation query exceeded 2 seconds", System.currentTimeMillis() - relationStarted < 2_000)

            val backup = java.io.File(context.cacheDir, "capacity.lexibackup")
            val settings = object : SettingsRepository {
                override val settings: Flow<UserSettings> = flowOf(UserSettings())
                override suspend fun replaceSettings(settings: UserSettings) = Unit
                override suspend fun setDailyNewWordLimit(value: Int) = Unit
                override suspend fun setDailyReviewLimit(value: Int) = Unit
                override suspend fun setAutoPlayAudio(value: Boolean) = Unit
                override suspend fun setSpeechRate(value: Float) = Unit
                override suspend fun setReminderEnabled(value: Boolean) = Unit
                override suspend fun setReminderTime(hour: Int, minute: Int) = Unit
            }
            val backupService = LexiBackupService(db, settings, context.cacheDir, appVersion = "capacity")
            backup.outputStream().use { assertEquals(200_000, backupService.export(it).words) }
            backup.inputStream().use { assertEquals(200_000, backupService.restore(it).words) }
            assertEquals(200_000, db.lexiconDao().count().first())
            backup.delete()
            Unit
        } finally { db.close() }
    }
}
