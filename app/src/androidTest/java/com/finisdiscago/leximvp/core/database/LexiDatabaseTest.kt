package com.finisdiscago.leximvp.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.finisdiscago.leximvp.core.database.entity.ArticleEntity
import com.finisdiscago.leximvp.core.database.entity.ArticleTokenEntity
import com.finisdiscago.leximvp.core.database.entity.ReviewCardEntity
import com.finisdiscago.leximvp.core.database.entity.ReviewStateEntity
import com.finisdiscago.leximvp.core.database.entity.VocabularyBookEntity
import com.finisdiscago.leximvp.core.database.entity.WordEntryEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LexiDatabaseTest {
    private lateinit var database: LexiDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            LexiDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun deletingBookCascadesToWordsCardsAndStates() = runTest {
        val now = 1_000L
        val book = VocabularyBookEntity(
            name = "Core vocabulary",
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now
        )
        val bookId = database.vocabularyDao().insertBook(book)
        val wordId = database.vocabularyDao().insertWord(
            WordEntryEntity(
                stableId = "test:database:alpha",
                bookId = bookId,
                word = "excess",
                normalizedWord = "excess",
                meaning = "过量",
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now
            )
        )
        val cardId = database.reviewDao().insertCards(
            listOf(
                ReviewCardEntity(
                    stableId = "test:card:learning",
                    wordId = wordId,
                    cardType = "ForeignToMeaning",
                    createdAtEpochMillis = now
                )
            )
        ).single()
        database.reviewDao().upsertStates(
            listOf(
                ReviewStateEntity(
                    cardId = cardId,
                    status = "Review",
                    dueAtEpochMillis = now
                )
            )
        )

        database.vocabularyDao().deleteBook(book.copy(id = bookId))

        assertTrue(database.reviewDao().getDueCards(now, null, 10).isEmpty())
        assertTrue(database.vocabularyDao().findWords(listOf("excess")).isEmpty())
    }

    @Test
    fun dueQueryIncludesDueMasteredAndExcludesFutureAndSuspendedCards() = runTest {
        val now = 10_000L
        val wordId = insertWord(now)
        val statuses = listOf(
            Pair("Review", now - 1),
            Pair("Review", now + 1),
            Pair("Mastered", now - 1),
            Pair("Mastered", now + 1),
            Pair("Suspended", now - 1)
        )

        statuses.forEachIndexed { index, (status, dueAt) ->
            val cardId = database.reviewDao().insertCards(
                listOf(
                    ReviewCardEntity(
                        stableId = "test:card:$index",
                        wordId = wordId,
                        cardType = "type-$index",
                        createdAtEpochMillis = now
                    )
                )
            ).single()
            database.reviewDao().upsertStates(
                listOf(
                    ReviewStateEntity(
                        cardId = cardId,
                        status = status,
                        dueAtEpochMillis = dueAt
                    )
                )
            )
        }

        val dueCards = database.reviewDao().getDueCards(now, null, 10)

        assertEquals(setOf("type-0", "type-2"), dueCards.map { it.cardType }.toSet())
    }

    @Test
    fun articleWordStatusUpdatesEveryOccurrence() = runTest {
        val now = 20_000L
        val articleId = database.articleDao().insertArticle(
            ArticleEntity(
                title = "Example",
                content = "Excess creates excess.",
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now
            )
        )
        database.articleDao().replaceTokens(
            articleId = articleId,
            tokens = listOf(
                ArticleTokenEntity(
                    articleId = articleId,
                    rawText = "Excess",
                    normalizedText = "excess",
                    startOffset = 0,
                    endOffset = 6,
                    learningStatus = "Unlearned"
                ),
                ArticleTokenEntity(
                    articleId = articleId,
                    rawText = "excess",
                    normalizedText = "excess",
                    startOffset = 15,
                    endOffset = 21,
                    learningStatus = "Unlearned"
                )
            )
        )

        database.articleDao().updateWordStatus(
            articleId = articleId,
            normalizedText = "excess",
            learningStatus = "Unknown"
        )

        val tokens = database.articleDao().getTokens(articleId)
        assertEquals(2, tokens.size)
        assertTrue(tokens.all { it.learningStatus == "Unknown" })
    }

    private suspend fun insertWord(now: Long): Long {
        val bookId = database.vocabularyDao().insertBook(
            VocabularyBookEntity(
                name = "Book-$now",
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now
            )
        )
        return database.vocabularyDao().insertWord(
            WordEntryEntity(
                stableId = "test:database:beta",
                bookId = bookId,
                word = "word",
                normalizedWord = "word",
                meaning = "词",
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now
            )
        )
    }
}
