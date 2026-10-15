package com.finisdiscago.leximvp.product

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.finisdiscago.leximvp.core.database.LexiDatabase
import com.finisdiscago.leximvp.core.database.RoomArticleRepository
import com.finisdiscago.leximvp.core.database.RoomReviewRepository
import com.finisdiscago.leximvp.core.database.RoomVocabularyRepository
import com.finisdiscago.leximvp.core.database.StudyCardFactory
import com.finisdiscago.leximvp.core.database.entity.ExampleEntity
import com.finisdiscago.leximvp.core.database.entity.SenseEntity
import com.finisdiscago.leximvp.core.database.entity.WorkspaceRecordEntity
import com.finisdiscago.leximvp.core.document.ImportedChapter
import com.finisdiscago.leximvp.core.document.ImportedDocument
import com.finisdiscago.leximvp.core.document.LocalDocumentImporter
import com.finisdiscago.leximvp.core.scheduler.ReviewRating
import com.finisdiscago.leximvp.domain.model.CardType
import com.finisdiscago.leximvp.domain.repository.WordInput
import com.finisdiscago.leximvp.feature.exam.ExamFormat
import com.finisdiscago.leximvp.feature.exam.ExamOcrParser
import kotlinx.coroutines.test.runTest
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class ProductClosureJourneyTest {
    private lateinit var context: Context
    private lateinit var database: LexiDatabase
    private lateinit var vocabulary: RoomVocabularyRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, LexiDatabase::class.java)
            .allowMainThreadQueries().build()
        vocabulary = RoomVocabularyRepository(database) { 1_000L }
    }

    @After
    fun tearDown() {
        database.close()
        File(context.filesDir, "closure-fixtures").deleteRecursively()
    }

    @Test
    fun sixExerciseTypesAndWrongAnswerDowngradeCompleteOneRepositoryJourney() = runTest {
        val bookId = vocabulary.createBook("Closure exercises", "")
        val wordId = vocabulary.createWord(WordInput(bookId, "adapt", "适应"))
        val dao = database.vocabularyDao()
        val word = requireNotNull(dao.getWord(wordId))
        dao.updateWord(word.copy(richContentJson = JSONObject()
            .put("exercises", JSONArray()
                .put(exercise("choice", CardType.AudioToChoice, "选择听到的词", "adapt", "adapt", "adopt"))
                .put(exercise("synonym", CardType.SynonymDiscrimination, "选择近义词", "adjust", "adjust", "refuse")))
            .toString()))
        dao.upsertSenses(listOf(SenseEntity("adapt:s1", wordId, 0, "verb", "适应", "change to fit")))
        dao.upsertExamples(listOf(ExampleEntity("adapt:e1", wordId, "adapt:s1", 0, "We adapt to change.", "我们适应变化。")))
        StudyCardFactory(database).ensure(wordId, 2_000L)

        val cards = database.reviewDao().getCardsForWord(wordId)
        val generatedTypes = cards.map { CardType.valueOf(it.cardType) }.toSet()
        assertEquals(CardType.entries.toSet(), generatedTypes)

        val review = RoomReviewRepository(database)
        database.reviewDao().getStatesForWord(wordId).forEach { state ->
            database.reviewDao().updateState(state.copy(status = "Review", dueAtEpochMillis = 1L, intervalDays = 2.0))
        }
        CardType.entries.forEach { type ->
            val card = cards.first { it.cardType == type.name }
            database.reviewDao().getStatesForWord(wordId).forEach { state ->
                database.reviewDao().updateState(state.copy(dueAtEpochMillis = if (state.cardId == card.id) 1L else 99_999L))
            }
            val item = requireNotNull(review.getNextDueCard(2L, 0L, 100, null))
            assertEquals(type, item.cardType)
            val answer = when (type) {
                CardType.AudioToChoice -> "adapt"
                CardType.SynonymDiscrimination -> "adjust"
                CardType.AudioToSpelling, CardType.SentenceCloze -> "adapt"
                else -> ""
            }
            val checked = review.checkAnswer(item, answer)
            if (type in objectiveTypes) assertTrue(checked.isCorrect == true) else assertEquals(null, checked.isCorrect)
        }

        val objectiveCard = cards.first { it.cardType == CardType.AudioToChoice.name }
        database.reviewDao().getStatesForWord(wordId).forEach { state ->
            database.reviewDao().updateState(state.copy(status = "Review", dueAtEpochMillis = if (state.cardId == objectiveCard.id) 1L else 99_999L))
        }
        val objective = requireNotNull(review.getNextDueCard(2L, 0L, 100, null))
        val wrong = review.checkAnswer(objective, "adopt")
        assertFalse(wrong.isCorrect == true)
        review.recordReview(objective, ReviewRating.Easy, wrong, 800L, 3_000L, "closure")
        assertEquals("Relearning", database.reviewDao().getStateForCard(objective.cardId)?.status)
    }

    @Test
    fun generatedLocalDocumentsCoverEpubPdfOcrImageAndMobi() = runTest {
        val fixtureDir = File(context.filesDir, "closure-fixtures").apply { mkdirs() }
        val importer = LocalDocumentImporter(context)
        val epub = importer.import(Uri.fromFile(buildEpub(File(fixtureDir, "illustrated.epub"))))
        assertEquals("EPUB", epub.format)
        assertEquals(2, epub.chapters.size)
        assertEquals(1, epub.images.size)
        assertTrue(File(context.filesDir, epub.images.single().localPath).exists())

        val textPdf = importer.import(Uri.fromFile(buildTextPdf(File(fixtureDir, "text.pdf"))))
        assertEquals("PDF-TEXT", textPdf.format)
        assertTrue(textPdf.chapters.single().content.contains("IELTS reading passage"))
        assertEquals("pdf:page=0", textPdf.chapters.single().locator)

        val scanPdf = importer.import(Uri.fromFile(buildScanPdf(File(fixtureDir, "scan.pdf"))))
        assertTrue(scanPdf.format in setOf("PDF-OCR", "PDF-MIXED"))
        assertEquals("pdf:page=0", scanPdf.chapters.single().locator)

        val image = importer.import(Uri.fromFile(buildImage(File(fixtureDir, "reading.png"))))
        assertEquals("IMAGE-OCR", image.format)
        assertEquals("image:0", image.chapters.single().locator)

        val mobi = importer.import(Uri.fromFile(buildMobi(File(fixtureDir, "classic.mobi"))))
        assertEquals("MOBI", mobi.format)
        assertTrue(mobi.text.contains("Classic MOBI reading"))
        val azw = importer.import(Uri.fromFile(buildMobi(File(fixtureDir, "classic.azw"))))
        assertEquals(mobi.text, azw.text)
        val encrypted = buildMobi(File(fixtureDir, "encrypted.azw"))
        val encryptedBytes = encrypted.readBytes().apply { this[107] = 1 }
        encrypted.writeBytes(encryptedBytes)
        val failure = runCatching { importer.import(Uri.fromFile(encrypted)) }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("DRM"))
        assertTrue(encrypted.readBytes().contentEquals(encryptedBytes))
    }

    @Test
    fun importedReadingUnknownAndThreeExamSourcesCloseTheContentLoop() = runTest {
        val bookId = vocabulary.createBook("Reading unknowns", "")
        val articles = RoomArticleRepository(database, vocabulary) { 5_000L }
        val articleId = articles.createArticle("Imported chapter", "Criteria change quickly.", "epub:chapter.xhtml")
        val reading = requireNotNull(articles.getReading(articleId))
        val token = reading.tokens.first { it.rawText == "Criteria" }
        val pendingId = articles.addUnknownWord(articleId, token.normalizedText, bookId, "")
        val pending = requireNotNull(database.vocabularyDao().getWord(pendingId))
        assertEquals("Pending", pending.intakeStatus)
        assertTrue(database.reviewDao().getCardsForWord(pendingId).isEmpty())
        assertEquals("Criteria", database.vocabularyDao().getWordForms(pendingId).single().form)
        articles.markUnknown(articleId, token.normalizedText, 6_000L)
        assertEquals(1, articles.getReading(articleId)?.tokens?.count { it.status.name == "Learning" })

        val structured = ExamFormat.validate("""{"title":"JSON paper","questions":[{"id":"q1","type":"single","prompt":"Choose","options":["A","B"],"answer":"A"},{"id":"q2","type":"translation","prompt":"Translate","answer":"Reference"}]}""")
        assertTrue(ExamFormat.grade(structured.getJSONArray("questions").getJSONObject(0), "A") == true)
        assertEquals(null, ExamFormat.grade(structured.getJSONArray("questions").getJSONObject(1), "Reference"))

        listOf("PDF-OCR", "IMAGE-OCR").forEach { format ->
            val draft = ExamOcrParser.draft(ImportedDocument("$format paper", format,
                listOf(ImportedChapter("第 1 页", "1. Choose the answer\nA. Alpha\nB. Beta", "source:0"))))
            val paper = ExamFormat.validate(draft)
            assertEquals("single", paper.getJSONArray("questions").getJSONObject(0).getString("type"))
            database.lexiconDao().save(WorkspaceRecordEntity(kind = "exam", title = paper.getString("title"), payload = paper.toString(), updatedAtEpochMillis = 7_000L))
        }
        assertEquals(2, database.lexiconDao().allRecords().count { it.kind == "exam" })
    }

    private fun exercise(id: String, type: CardType, prompt: String, answer: String, vararg options: String) =
        JSONObject().put("id", id).put("type", type.name).put("prompt", prompt).put("answer", answer)
            .put("options", JSONArray(options.toList())).put("explanation", "fixture")

    private fun buildEpub(file: File): File {
        ZipOutputStream(file.outputStream()).use { zip ->
            fun entry(name: String, bytes: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
            entry("META-INF/container.xml", """<container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>""".toByteArray())
            entry("OEBPS/content.opf", """<package><manifest><item id="one" href="one.xhtml"/><item id="two" href="two.xhtml"/><item id="pic" href="pic.png"/></manifest><spine><itemref idref="one"/><itemref idref="two"/></spine></package>""".toByteArray())
            entry("OEBPS/one.xhtml", """<html><head><title>One</title></head><body><p>Illustrated reading.</p><img src="pic.png" alt="chart"/></body></html>""".toByteArray())
            entry("OEBPS/two.xhtml", """<html><head><title>Two</title></head><body><p>Second chapter.</p></body></html>""".toByteArray())
            entry("OEBPS/pic.png", imageBytes())
        }
        return file
    }

    private fun buildTextPdf(file: File): File {
        PDFBoxResourceLoader.init(context)
        PDDocument().use { document ->
            val page = PDPage(); document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText(); stream.setFont(PDType1Font.HELVETICA, 18f); stream.newLineAtOffset(72f, 700f)
                stream.showText("IELTS reading passage"); stream.endText()
            }
            document.save(file)
        }
        return file
    }

    private fun buildScanPdf(file: File): File {
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(800, 600, 1).create())
        page.canvas.drawColor(Color.WHITE)
        val rasterBytes = imageBytes()
        val raster = BitmapFactory.decodeByteArray(rasterBytes, 0, rasterBytes.size)
        page.canvas.drawBitmap(raster, null, Rect(60, 150, 700, 390), null)
        raster.recycle()
        document.finishPage(page); file.outputStream().use(document::writeTo); document.close()
        return file
    }

    private fun buildImage(file: File): File {
        file.writeBytes(imageBytes())
        return file
    }

    private fun imageBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(640, 240, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply { drawColor(Color.WHITE); drawText("READING IMAGE", 30f, 140f, Paint().apply { color = Color.BLACK; textSize = 64f }) }
        val output = java.io.ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); bitmap.recycle()
        return output.toByteArray()
    }

    private fun buildMobi(file: File): File {
        val text = "<html><body>Classic MOBI reading.</body></html>".toByteArray()
        val firstOffset = 94
        val secondOffset = 160
        val bytes = ByteArray(secondOffset + text.size)
        fun u16(offset: Int, value: Int) { bytes[offset] = (value shr 8).toByte(); bytes[offset + 1] = value.toByte() }
        fun u32(offset: Int, value: Int) { bytes[offset] = (value shr 24).toByte(); bytes[offset + 1] = (value shr 16).toByte(); bytes[offset + 2] = (value shr 8).toByte(); bytes[offset + 3] = value.toByte() }
        u16(76, 2); u32(78, firstOffset); u32(86, secondOffset)
        u16(firstOffset, 1); u32(firstOffset + 4, text.size); u16(firstOffset + 8, 1); u16(firstOffset + 12, 0)
        "MOBI".toByteArray().copyInto(bytes, firstOffset + 16); u32(firstOffset + 28, 65001)
        text.copyInto(bytes, secondOffset); file.writeBytes(bytes)
        return file
    }

    private companion object {
        val objectiveTypes = setOf(CardType.AudioToChoice, CardType.AudioToSpelling, CardType.SentenceCloze, CardType.SynonymDiscrimination)
    }
}
