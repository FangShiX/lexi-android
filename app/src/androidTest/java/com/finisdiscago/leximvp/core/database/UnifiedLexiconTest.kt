package com.finisdiscago.leximvp.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.finisdiscago.leximvp.core.text.ContentMergeService
import com.finisdiscago.leximvp.core.text.ModuleRegistry
import com.finisdiscago.leximvp.core.text.RichWordContentParser
import com.finisdiscago.leximvp.core.scheduler.ReviewRating
import com.finisdiscago.leximvp.domain.model.*
import com.finisdiscago.leximvp.domain.repository.WordInput
import com.finisdiscago.leximvp.feature.exam.ExamFormat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class UnifiedLexiconTest {
    private fun database(): LexiDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LexiDatabase::class.java).build()

    @Test fun optionalAndBrokenModulesDoNotHideOtherContent() {
        val raw = """{"word":"inchoate","senses":[{"meaningZh":"初期的","examples":[{"en":"An inchoate idea."}]}],"etymology":42,"modules":[{"id":"future","type":"future","version":9,"content":{"keep":true}}]}"""
        assertEquals("初期的", RichWordContentParser.parse(raw).senses.single().meaningZh)
        val modules = ModuleRegistry.read(raw)
        assertNotNull(modules.first { it.type == "etymology" }.warning)
        assertTrue(modules.first { it.id == "future" }.payload.contains("keep"))
        val edited = ModuleRegistry.add(raw, "text", "我的笔记", "记住语境")
        assertTrue(ModuleRegistry.read(edited).any { it.title == "我的笔记" })
    }

    @Test fun mergeIsIdempotentAndPreservesUserContent() {
        val first = ContentMergeService.merge("""{"meaning":"我的释义"}""", """{"meaning":"新释义","notes":"补充"}""")
        assertEquals("我的释义", JSONObject(first.json).getString("meaning"))
        assertEquals("补充", JSONObject(first.json).getString("notes"))
        val again = ContentMergeService.merge(first.json, """{"meaning":"新释义","notes":"补充"}""")
        assertEquals(1, JSONObject(again.json).getJSONArray("importConflicts").length())
    }

    @Test fun minimalImportAndRepeatedUpdateKeepOneWordAndNoEagerCards() = runTest {
        val db = database()
        try {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val importer = VocabularyPackageImporter(db, context.cacheDir)
            val first = importer.import(ByteArrayInputStream(pack("""{"word":"inchoate"}""")))
            val word = db.backupDao().getWords().single()
            assertEquals("", word.meaning)
            assertTrue(db.reviewDao().getCardsForWord(word.id).isEmpty())
            importer.import(ByteArrayInputStream(pack("""{"word":"inchoate","meaning":"初期的"}""")))
            assertEquals(1, db.backupDao().countWords())
            assertEquals(word.id, db.backupDao().getWords().single().id)
            assertEquals("初期的", db.backupDao().getWords().single().meaning)
            assertEquals(1, first.entryCount)
            DailyStudyPlanner(db).ensurePlan("2026-09-29", 20)
            assertEquals(3, db.reviewDao().getCardsForWord(word.id).size)
            assertEquals(1, db.lexiconDao().page("inc", "", 0, "", "", "alphabet", 50, 0).first().size)
        } finally { db.close() }
    }

    @Test fun objectiveErrorOverridesEasyAndManualMastery() = runTest {
        val db = database()
        try {
            val words = RoomVocabularyRepository(db)
            val source = words.createBook("test", "")
            val id = words.createWord(WordInput(source, "adapt", "适应"))
            words.setWordFamiliarity(id, "Mastered")
            val card = db.reviewDao().getCardsForWord(id).first { it.cardType == "AudioToSpelling" }
            RoomReviewRepository(db).recordReview(ReviewQueueItem(card.id, id, CardType.AudioToSpelling, "adapt", "适应", null, null, null,
                dueAtEpochMillis = 0, intervalDays = 30.0, easeFactor = 2.5), ReviewRating.Easy, ReviewAnswerResult(false, "wrong"), 1000, 1000, "test")
            assertEquals("Relearning", db.reviewDao().getStateForCard(card.id)?.status)
            assertNull(db.vocabularyDao().getWord(id)?.manualFamiliarity)
            assertTrue(db.vocabularyDao().getWord(id)!!.proficiencyScore < 0.6)
        } finally { db.close() }
    }

    @Test fun examNeverInventsScoreForMissingOrSubjectiveAnswers() {
        val paper = ExamFormat.validate("""{"title":"Test","questions":[{"id":"a","type":"single","prompt":"Choose","options":["A","B"]},{"id":"b","type":"writing","prompt":"Write","answer":"Reference"}]}""")
        assertNull(ExamFormat.grade(paper.getJSONArray("questions").getJSONObject(0), "A"))
        assertNull(ExamFormat.grade(paper.getJSONArray("questions").getJSONObject(1), "Reference"))
    }

    private fun pack(entry: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write("""{"schemaVersion":2,"source":{"name":"Advanced","entryCount":1}}""".toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("entries.jsonl")); zip.write((entry + "\n").toByteArray()); zip.closeEntry()
        }
        return out.toByteArray()
    }
}
