package com.finisdiscago.leximvp.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class VocabularyPackageImporterTest {
    private lateinit var context: Context
    private lateinit var database: LexiDatabase
    private lateinit var repository: RoomVocabularyRepository
    private lateinit var importer: VocabularyPackageImporter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, LexiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomVocabularyRepository(database)
        importer = VocabularyPackageImporter(database, context.cacheDir) { 1_000L }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun importsThreeWordsWithCardsAndRichContent() = runTest {
        val progress = mutableListOf<VocabularyImportProgress>()
        val result = importer.import(
            ByteArrayInputStream(packageBytes(listOf("adapt", "derive", "infer"))),
            progress::add
        )

        val books = repository.observeBooks().first()
        val words = repository.observeWords(result.bookId, "").first()

        assertEquals(1, books.size)
        assertEquals("考研测试词库", result.bookName)
        assertEquals(3, result.entryCount)
        assertEquals(3, words.size)
        assertTrue(words.all { it.richContentJson != null })
        assertEquals("adapt", words.first().normalizedWord)
        assertEquals("v. adapt 的释义", words.first().meaning)
        assertEquals("/adapt-uk/ / /adapt-us/", words.first().pronunciation)
        assertNotNull(JSONObject(words.first().richContentJson!!).getJSONObject("etymology"))
        assertTrue(words.all { database.reviewDao().getCardsForWord(it.id).isEmpty() })
        assertTrue(words.all { database.reviewDao().getStatesForWord(it.id).isEmpty() })
        assertEquals(VocabularyImportProgress(3, 3), progress.last())

        val learningItem = RoomLearningRepository(
            database,
            DailyStudyPlanner(database) { 1_000L }
        ).getNextDueWord(
            nowEpochMillis = 1_000L,
            dayStartEpochMillis = 0L,
            dailyNewWordLimit = 20
        )
        assertNotNull(learningItem?.richContent)
        assertEquals(2, learningItem?.matchedSense?.examples?.size)
    }

    @Test
    fun keepsHeadwordsThatShareAnInflectionStemDistinct() = runTest {
        val result = importer.import(
            ByteArrayInputStream(packageBytes(listOf("new", "news")))
        )

        val words = repository.observeWords(result.bookId, "").first()
        assertEquals(listOf("new", "news"), words.map { it.normalizedWord })
    }

    @Test
    fun mergesWordsAlreadyOwnedByAnOlderSourceWithoutOverwritingUserMeaning() = runTest {
        val existingBookId = repository.createBook("Existing", "")
        val existingWordId = repository.createWord(
            com.finisdiscago.leximvp.domain.repository.WordInput(
                bookId = existingBookId,
                word = "adapt",
                meaning = "旧释义"
            )
        )

        val result = importer.import(
            ByteArrayInputStream(packageBytes(listOf("adapt", "derive")))
        )

        assertEquals(1, result.entryCount)
        assertEquals(0, result.skippedDuplicateCount)
        assertEquals(1, result.updatedCount)
        assertEquals(existingWordId, repository.getWord(existingWordId)?.id)
        assertEquals("旧释义", repository.getWord(existingWordId)?.meaning)
        assertEquals(
            listOf("adapt", "derive"),
            repository.observeWords(result.bookId, "").first().map { it.normalizedWord }
        )
    }

    @Test
    fun mismatchedEntryCountRollsBackEntireImport() = runTest {
        var failed = false
        try {
            importer.import(
                ByteArrayInputStream(
                    packageBytes(listOf("adapt", "derive"), declaredCount = 3)
                )
            )
        } catch (_: VocabularyPackageException) {
            failed = true
        }

        assertTrue(failed)
        assertTrue(repository.observeBooks().first().isEmpty())
    }

    @Test
    fun cancellationRollsBackEntireImport() = runTest {
        var cancelled = false
        try {
            importer.import(
                ByteArrayInputStream(packageBytes((1..101).map { "word$it" }))
            ) { progress ->
                if (progress.importedEntries == 100) {
                    throw CancellationException("test cancellation")
                }
            }
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        assertTrue(repository.observeBooks().first().isEmpty())
    }

    @Test
    fun rejectsPackageThatExceedsConfiguredSizeLimit() = runTest {
        val limitedImporter = VocabularyPackageImporter(
            database = database,
            cacheDirectory = context.cacheDir,
            limits = VocabularyImportLimits(maxPackageBytes = 32),
            now = { 1_000L }
        )
        var failed = false

        try {
            limitedImporter.import(ByteArrayInputStream(packageBytes(listOf("adapt"))))
        } catch (_: VocabularyPackageException) {
            failed = true
        }

        assertTrue(failed)
        assertTrue(repository.observeBooks().first().isEmpty())
    }

    private fun packageBytes(
        words: List<String>,
        declaredCount: Int = words.size
    ): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(
                JSONObject()
                    .put("schemaVersion", 1)
                    .put(
                        "book",
                        JSONObject()
                            .put("id", "test-book")
                            .put("name", "考研测试词库")
                            .put("entryCount", declaredCount)
                            .put(
                                "source",
                                JSONObject()
                                    .put("name", "test")
                                    .put("license", "CC BY-NC-SA 4.0")
                            )
                    )
                    .toString()
                    .toByteArray()
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("entries.jsonl"))
            words.forEach { word ->
                zip.write((entryJson(word).toString() + "\n").toByteArray())
            }
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private fun entryJson(word: String): JSONObject = JSONObject()
        .put("word", word)
        .put(
            "pronunciation",
            JSONObject()
                .put("ukIpa", "/$word-uk/")
                .put("usIpa", "/$word-us/")
                .put("syllables", word)
                .put("stressNote", "测试重音")
        )
        .put(
            "basicMeanings",
            JSONArray().put(
                JSONObject()
                    .put("partOfSpeech", "v.")
                    .put("meaningsZh", JSONArray().put("$word 的释义"))
            )
        )
        .put(
            "coreMeaning",
            JSONObject()
                .put("nativeConcept", "$word 的核心意义")
                .put("senseConnections", "义项联系")
        )
        .put(
            "etymology",
            JSONObject()
                .put("origin", "测试词源")
                .put("development", "测试演变")
                .put("confidenceNote", "高")
        )
        .put(
            "senses",
            JSONArray().put(
                JSONObject()
                    .put("partOfSpeech", "v.")
                    .put("meaningZh", "$word 的释义")
                    .put("definitionEn", "test definition")
                    .put("contexts", JSONArray().put("日常"))
                    .put("frequency", "common")
                    .put("register", JSONArray().put("neutral"))
                    .put("connotation", "中性")
                    .put("semanticBoundary", "测试边界")
                    .put("collocations", JSONArray().put("$word well"))
                    .put("patterns", JSONArray().put("$word + object"))
                    .put("nativeAssociations", JSONArray().put("测试联想"))
                    .put(
                        "examples",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("en", "We use $word here.")
                                    .put("zh", "我们在这里使用这个词。")
                            )
                            .put(
                                JSONObject()
                                    .put("en", "They also use $word.")
                                    .put("zh", "他们也使用这个词。")
                            )
                    )
            )
        )
        .put(
            "wordFormation",
            JSONObject()
                .put("morphemes", JSONArray())
                .put("forms", JSONArray())
                .put("family", JSONArray())
        )
        .put("confusables", JSONArray())
        .put(
            "semanticNetwork",
            JSONObject()
                .put("antonyms", JSONArray())
                .put("hypernyms", JSONArray())
                .put("hyponyms", JSONArray())
                .put("related", JSONArray())
        )
        .put("chunks", JSONArray())
        .put(
            "source",
            JSONObject()
                .put("category", "测试")
                .put("subcategory", "集成测试")
        )
}
