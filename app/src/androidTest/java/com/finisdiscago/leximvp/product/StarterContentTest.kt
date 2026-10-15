package com.finisdiscago.leximvp.product

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.finisdiscago.leximvp.core.database.LexiDatabase
import com.finisdiscago.leximvp.core.database.VocabularyPackageImporter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterContentTest {
    @Test fun packagedStarterImportsAndDuplicateImportPreservesOneLexicon() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LexiDatabase::class.java).build()
        try {
            val importer = VocabularyPackageImporter(db, context.cacheDir)
            val first = context.assets.open("starter.lexipack").use { importer.import(it) }
            assertEquals(10, first.entryCount)
            assertEquals(10, db.lexiconDao().count().first())
            context.assets.open("starter.lexipack").use { importer.import(it) }
            assertEquals(10, db.lexiconDao().count().first())
            assertTrue(db.lexiconDao().page("learn", "", 0, "", "", "alphabet", 50, 0).first().any { it.word == "learn" })
        } finally { db.close() }
    }
}
