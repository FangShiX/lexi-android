package com.finisdiscago.leximvp.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.finisdiscago.leximvp.core.database.entity.LearningAttemptEntity
import com.finisdiscago.leximvp.domain.repository.WordInput
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomStatisticsRepositoryTest {
    private lateinit var database: LexiDatabase
    private lateinit var vocabularyRepository: RoomVocabularyRepository
    private lateinit var statisticsRepository: RoomStatisticsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            LexiDatabase::class.java
        ).allowMainThreadQueries().build()
        vocabularyRepository = RoomVocabularyRepository(database) { 1_000L }
        statisticsRepository = RoomStatisticsRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun emptyDatabaseReturnsZeroStatistics() = runTest {
        val statistics = statisticsRepository.getDailyStatistics(
            dayStartEpochMillis = 0L,
            dayEndEpochMillis = DAY,
            historyDayBoundaries = listOf(-DAY, 0L, DAY),
            futureDayBoundaries = listOf(0L, DAY, DAY * 2),
            dailyNewWordLimit = 20
        )

        assertEquals(0, statistics.newWordAttempts)
        assertEquals(0, statistics.reviewAttempts)
        assertEquals(0L, statistics.studyTimeMillis)
        assertEquals(listOf(0, 0), statistics.history.map { it.totalAttempts })
        assertEquals(listOf(0, 0), statistics.futureReviewLoad.map { it.count })
    }

    @Test
    fun aggregatesAttemptsAndFutureReviewLoad() = runTest {
        val bookId = vocabularyRepository.createBook("Core", "")
        val wordId = vocabularyRepository.createWord(
            WordInput(bookId, "word", "词")
        )
        val cardId = database.reviewDao().getCardsForWord(wordId).first().id
        database.reviewDao().insertAttempt(
            LearningAttemptEntity(
                sessionId = "new",
                wordId = wordId,
                cardId = cardId,
                rating = 3,
                responseTimeMillis = 2_000L,
                attemptKind = "NewWord",
                attemptedAtEpochMillis = 1_000L
            )
        )
        database.reviewDao().insertAttempt(
            LearningAttemptEntity(
                sessionId = "review",
                wordId = wordId,
                cardId = cardId,
                rating = 2,
                isCorrect = true,
                responseTimeMillis = 3_000L,
                attemptKind = "Review",
                attemptedAtEpochMillis = 2_000L
            )
        )
        database.reviewDao().getStatesForWord(wordId).forEachIndexed { index, state ->
            database.reviewDao().updateState(
                state.copy(
                    status = "Review",
                    dueAtEpochMillis = if (index == 0) 10_000L else DAY + 10_000L
                )
            )
        }

        val statistics = statisticsRepository.getDailyStatistics(
            dayStartEpochMillis = 0L,
            dayEndEpochMillis = DAY,
            historyDayBoundaries = listOf(-DAY, 0L, DAY),
            futureDayBoundaries = listOf(0L, DAY, DAY * 2),
            dailyNewWordLimit = 20
        )

        assertEquals(1, statistics.newWordAttempts)
        assertEquals(1, statistics.reviewAttempts)
        assertEquals(1, statistics.masteredRatings)
        assertEquals(1, statistics.correctAnswers)
        assertEquals(1, statistics.answeredQuestions)
        assertEquals(5_000L, statistics.studyTimeMillis)
        assertEquals(listOf(0, 2), statistics.history.map { it.totalAttempts })
        assertEquals(listOf(1, 2), statistics.futureReviewLoad.map { it.count })
        assertTrue(statistics.accuracy == 1f)
    }

    @Test
    fun projectsFutureNewWordsFromBookLimitsAndRemainingPendingWords() = runTest {
        val bookId = vocabularyRepository.createBook("Plan", "")
        val wordIds = (1..3).map { index ->
            vocabularyRepository.createWord(
                WordInput(bookId, "planned$index", "计划$index")
            )
        }
        database.vocabularyDao().updateIntakeStatus(wordIds, "Pending", 1_000L)
        vocabularyRepository.updateBookPlan(bookId, true, 2, 10)

        val statistics = statisticsRepository.getDailyStatistics(
            dayStartEpochMillis = 0L,
            dayEndEpochMillis = DAY,
            historyDayBoundaries = listOf(-DAY, 0L),
            futureDayBoundaries = listOf(0L, DAY, DAY * 2, DAY * 3),
            dailyNewWordLimit = 2
        )

        assertEquals(
            listOf(2, 1, 0),
            statistics.futureReviewLoad.map { it.newWordCount }
        )
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1_000L
    }
}
