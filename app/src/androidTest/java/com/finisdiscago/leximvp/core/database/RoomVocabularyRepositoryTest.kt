package com.finisdiscago.leximvp.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.finisdiscago.leximvp.domain.model.CardType
import com.finisdiscago.leximvp.domain.repository.DuplicateWordException
import com.finisdiscago.leximvp.domain.repository.WordInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomVocabularyRepositoryTest {
    private lateinit var database: LexiDatabase
    private lateinit var repository: RoomVocabularyRepository
    private var timestamp = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            LexiDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = RoomVocabularyRepository(database) { timestamp++ }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun creatingBasicWordGeneratesOnlySupportedCardsAndInitialStates() = runTest {
        val bookId = repository.createBook("Core", "")

        val wordId = repository.createWord(
            WordInput(
                bookId = bookId,
                word = "Excess",
                meaning = "过量",
                tags = listOf("考研", "重点", "重点")
            )
        )

        val word = repository.getWord(wordId)
        val cards = database.reviewDao().getCardsForWord(wordId)
        val states = database.reviewDao().getStatesForWord(wordId)

        assertEquals("excess", word?.normalizedWord)
        assertEquals(listOf("考研", "重点"), word?.tags)
        assertEquals(
            listOf(
                CardType.AudioToSpelling,
                CardType.ForeignToMeaning,
                CardType.MeaningToForeign
            ).map { it.name }.sorted(),
            cards.map { it.cardType }
        )
        assertEquals(3, states.size)
        assertTrue(states.all { it.status == "New" })
    }

    @Test
    fun duplicateNormalizedWordIsRejectedWithinSameBook() = runTest {
        val bookId = repository.createBook("Core", "")
        repository.createWord(
            WordInput(bookId = bookId, word = "Excess", meaning = "过量")
        )

        var duplicateRejected = false
        try {
            repository.createWord(
                WordInput(bookId = bookId, word = " excess ", meaning = "超过")
            )
        } catch (_: DuplicateWordException) {
            duplicateRejected = true
        }
        assertTrue(duplicateRejected)
    }

    @Test
    fun sameWordIsRejectedAcrossDifferentBooks() = runTest {
        val firstBook = repository.createBook("First", "")
        val secondBook = repository.createBook("Second", "")

        repository.createWord(WordInput(firstBook, "word", "词"))
        var duplicateRejected = false
        try {
            repository.createWord(WordInput(secondBook, "word", "单词"))
        } catch (_: DuplicateWordException) {
            duplicateRejected = true
        }

        assertTrue(duplicateRejected)
        assertEquals(1, repository.observeWords(firstBook, "").first().size)
        assertTrue(repository.observeWords(secondBook, "").first().isEmpty())
    }

    @Test
    fun manualFamiliarityUpdatesSchedulingAndCanBeCleared() = runTest {
        val bookId = repository.createBook("Familiarity", "")
        val wordId = repository.createWord(
            WordInput(bookId, "durable", "持久的")
        )

        repository.setWordFamiliarity(wordId, "Familiar")
        assertEquals(1, repository.observeBooks().first().single().learnedWordCount)
        assertEquals(0.6, repository.getWord(wordId)?.proficiencyScore ?: -1.0, 0.0)

        repository.setWordFamiliarity(wordId, null)
        assertEquals(1, repository.observeBooks().first().single().learnedWordCount)
        assertEquals(null, repository.getWord(wordId)?.manualFamiliarity)
        assertEquals(0.6, repository.getWord(wordId)?.proficiencyScore ?: -1.0, 0.0)
    }
}
