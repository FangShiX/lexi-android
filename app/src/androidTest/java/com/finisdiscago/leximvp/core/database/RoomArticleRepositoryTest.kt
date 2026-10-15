package com.finisdiscago.leximvp.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.finisdiscago.leximvp.domain.model.ArticleWordStatus
import com.finisdiscago.leximvp.domain.repository.WordInput
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomArticleRepositoryTest {
    private lateinit var database: LexiDatabase
    private lateinit var vocabularyRepository: RoomVocabularyRepository
    private lateinit var articleRepository: RoomArticleRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            LexiDatabase::class.java
        ).allowMainThreadQueries().build()
        vocabularyRepository = RoomVocabularyRepository(database) { 1_000L }
        articleRepository = RoomArticleRepository(
            database = database,
            vocabularyRepository = vocabularyRepository,
            now = { 2_000L }
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun creatingArticleMatchesVocabularyAndPreservesUnknownWords() = runTest {
        val bookId = vocabularyRepository.createBook("Core", "")
        vocabularyRepository.createWord(
            WordInput(bookId, "excess", "过量")
        )

        val articleId = articleRepository.createArticle(
            title = "Example",
            content = "Excess and access."
        )
        val reading = requireNotNull(articleRepository.getReading(articleId))

        val excess = reading.tokens.first { it.normalizedText == "excess" }
        val access = reading.tokens.first { it.normalizedText == "access" }
        assertNotNull(excess.wordId)
        assertEquals("过量", excess.meaning)
        assertEquals(ArticleWordStatus.Unlearned, excess.status)
        assertEquals(null, access.wordId)
        assertEquals(ArticleWordStatus.Unknown, access.status)
    }

    @Test
    fun markingExistingWordUnknownResetsAllCardsToNew() = runTest {
        val bookId = vocabularyRepository.createBook("Core", "")
        val wordId = vocabularyRepository.createWord(
            WordInput(bookId, "excess", "过量")
        )
        database.reviewDao().getStatesForWord(wordId).forEach { state ->
            database.reviewDao().updateState(
                state.copy(status = "Review", dueAtEpochMillis = 99_000L)
            )
        }
        val articleId = articleRepository.createArticle("Example", "Excess.")

        articleRepository.markUnknown(articleId, "excess", 5_000L)

        val states = database.reviewDao().getStatesForWord(wordId)
        assertTrue(states.all { it.status == "New" })
        assertTrue(states.all { it.dueAtEpochMillis == 5_000L })
        assertEquals(
            ArticleWordStatus.Learning,
            articleRepository.getReading(articleId)?.tokens?.single()?.status
        )
    }

    @Test
    fun addingUnknownWordLinksEveryOccurrenceAndCreatesCards() = runTest {
        val bookId = vocabularyRepository.createBook("Core", "")
        val articleId = articleRepository.createArticle(
            title = "Repeated",
            content = "Access grants access."
        )

        val wordId = articleRepository.addUnknownWord(
            articleId = articleId,
            normalizedText = "access",
            bookId = bookId,
            meaning = "进入；访问"
        )

        val matchingTokens = articleRepository.getReading(articleId)
            ?.tokens
            .orEmpty()
            .filter { it.normalizedText == "access" }
        assertEquals(2, matchingTokens.size)
        assertTrue(matchingTokens.all { it.wordId == wordId })
        assertTrue(matchingTokens.all { it.status == ArticleWordStatus.Learning })
        assertEquals(3, database.reviewDao().getCardsForWord(wordId).size)
    }
}
