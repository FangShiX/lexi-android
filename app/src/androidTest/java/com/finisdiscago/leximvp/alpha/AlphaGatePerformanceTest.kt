package com.finisdiscago.leximvp.alpha

import android.content.Context
import android.os.Debug
import android.os.SystemClock
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.finisdiscago.leximvp.core.database.LexiDatabase
import com.finisdiscago.leximvp.core.database.RoomVocabularyRepository
import com.finisdiscago.leximvp.core.database.VocabularyPackageImporter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AlphaGatePerformanceTest {
    @Test
    fun imports5500RichGeneratedEntriesWithinBudget() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageFile = File(context.cacheDir, "import-performance.lexipack")
        java.util.zip.ZipOutputStream(packageFile.outputStream().buffered()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
            zip.write(org.json.JSONObject().put("schemaVersion", 2).put("source", org.json.JSONObject()
                .put("id", "performance:5500").put("name", "Generated performance fixture")
                .put("entryCount", 5500).put("license", "Apache-2.0")).toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("entries.jsonl"))
            repeat(5500) { index ->
                val word = "fixture$index"
                val entry = org.json.JSONObject().put("id", "performance:$index").put("word", word)
                    .put("meaning", "合成性能测试词条 $index").put("language", "en")
                    .put("senses", org.json.JSONArray().put(org.json.JSONObject().put("id", "$word:s1")
                        .put("meaningZh", "合成释义 $index").put("definitionEn", "Generated benchmark entry")
                        .put("examples", org.json.JSONArray().put(org.json.JSONObject().put("en", "This fixture tests $word.").put("zh", "仅供导入性能测试。")))))
                    .put("modules", org.json.JSONArray().apply {
                        repeat(10) { module -> put(org.json.JSONObject().put("id", "$word:m$module")
                            .put("type", "text").put("title", "测试模块 $module")
                            .put("content", "Generated Unicode content for import, rollback and storage testing. ".repeat(8))) }
                    })
                zip.write((entry.toString() + "\n").toByteArray(Charsets.UTF_8))
            }
            zip.closeEntry()
        }
        val database = Room.inMemoryDatabaseBuilder(context, LexiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val beforePssKb = Debug.getPss()
            val startedAt = SystemClock.elapsedRealtime()
            val result = packageFile.inputStream().use { input ->
                VocabularyPackageImporter(database, context.cacheDir).import(input)
            }
            val elapsedMillis = SystemClock.elapsedRealtime() - startedAt
            val afterPssKb = Debug.getPss()
            val words = RoomVocabularyRepository(database)
                .observeWords(result.bookId, "")
                .first()

            assertEquals(5_500, result.entryCount)
            assertEquals(5_500, words.size)
            assertTrue("Import took ${elapsedMillis}ms", elapsedMillis <= MAX_IMPORT_MILLIS)
            assertTrue(
                "PSS grew by ${afterPssKb - beforePssKb}KB",
                afterPssKb - beforePssKb <= MAX_PSS_GROWTH_KB
            )
            println(
                "ALPHA_GATE importMillis=$elapsedMillis " +
                    "pssGrowthKb=${afterPssKb - beforePssKb} words=${words.size}"
            )
        } finally {
            database.close()
            packageFile.delete()
        }
    }

    private companion object {
        const val MAX_IMPORT_MILLIS = 120_000L
        const val MAX_PSS_GROWTH_KB = 350 * 1024
    }
}
