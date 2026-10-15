package com.finisdiscago.leximvp.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.finisdiscago.leximvp.core.scheduler.ReviewRating
import com.finisdiscago.leximvp.core.database.entity.LearningAttemptEntity
import com.finisdiscago.leximvp.domain.model.CardType
import com.finisdiscago.leximvp.domain.model.ReviewAnswerResult
import com.finisdiscago.leximvp.domain.model.ReviewQueueItem
import com.finisdiscago.leximvp.domain.repository.WordInput
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomReviewRepositoryTest {
    private lateinit var database: LexiDatabase
    private lateinit var vocabularyRepository: RoomVocabularyRepository
    private lateinit var reviewRepository: RoomReviewRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            LexiDatabase::class.java
        ).allowMainThreadQueries().build()
        vocabularyRepository = RoomVocabularyRepository(database) { 1_000L }
        reviewRepository = RoomReviewRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun queueAvoidsPreviouslyShownWordWhenAlternativeExists() = runTest {
        val bookId = vocabularyRepository.createBook("Core", "")
        val firstWordId = createReviewWord(bookId, "first", "第一")
        val secondWordId = createReviewWord(bookId, "second", "第二")

        val item = reviewRepository.getNextDueCard(
            nowEpochMillis = 2_000L,
            dayStartEpochMillis = 0L,
            dailyReviewLimit = 100,
            excludedWordId = firstWordId
        )

        assertNotNull(item)
        assertEquals(secondWordId, item?.wordId)
    }

    @Test
    fun audioSpellingAnswerUsesNormalizedComparison() = runTest {
        val item = ReviewQueueItem(
            cardId = 1,
            wordId = 1,
            cardType = CardType.AudioToSpelling,
            word = "Excess",
            meaning = "过量",
            pronunciation = null,
            exampleSentence = null,
            exampleTranslation = null,
            dueAtEpochMillis = 1_000L,
            intervalDays = 2.0,
            easeFactor = 2.5
        )

        assertTrue(reviewRepository.checkAnswer(item, " excess ").isCorrect == true)
        assertFalse(reviewRepository.checkAnswer(item, "access").isCorrect == true)
    }

    @Test
    fun againRecordsAttemptAndSchedulesTenMinutesLater() = runTest {
        val bookId = vocabularyRepository.createBook("Core", "")
        createReviewWord(bookId, "word", "词")
        val item = requireNotNull(
            reviewRepository.getNextDueCard(2_000L, 0L, 100, null)
        )

        reviewRepository.recordReview(
            item = item,
            rating = ReviewRating.Again,
            answerResult = ReviewAnswerResult(null, null),
            responseTimeMillis = 2_500L,
            reviewedAtEpochMillis = 10_000L,
            sessionId = "review-session"
        )

        val state = database.reviewDao().getStateForCard(item.cardId)
        val attempts = database.reviewDao().getWordAttempts(item.wordId, 0, 20_000L)
        assertEquals("Relearning", state?.status)
        assertEquals(610_000L, state?.dueAtEpochMillis)
        assertEquals(1, state?.lapseCount)
        assertEquals("Review", attempts.single().attemptKind)
    }

    @Test
    fun dailyReviewLimitStopsQueueAfterLimitIsReached() = runTest {
        val bookId = vocabularyRepository.createBook("Limited", "")
        val wordId = createReviewWord(bookId, "word", "词")
        val cardId = database.reviewDao().getCardsForWord(wordId).first().id
        database.reviewDao().insertAttempt(
            LearningAttemptEntity(
                sessionId = "done",
                wordId = wordId,
                cardId = cardId,
                rating = ReviewRating.Good.ordinal,
                responseTimeMillis = 1_000L,
                attemptKind = "Review",
                attemptedAtEpochMillis = 1_500L
            )
        )

        val item = reviewRepository.getNextDueCard(
            nowEpochMillis = 2_000L,
            dayStartEpochMillis = 0L,
            dailyReviewLimit = 1,
            excludedWordId = null
        )

        assertEquals(null, item)
        assertEquals(0, reviewRepository.countDueCards(2_000L, 0L, 1))
    }

    @Test
    fun dailyReviewLimitResetsAtNextDayBoundary() = runTest {
        val bookId = vocabularyRepository.createBook("Daily reset", "")
        val wordId = createReviewWord(bookId, "word", "word")
        val cardId = database.reviewDao().getCardsForWord(wordId).first().id
        database.reviewDao().insertAttempt(
            LearningAttemptEntity(
                sessionId = "day-one",
                wordId = wordId,
                cardId = cardId,
                rating = ReviewRating.Good.ordinal,
                responseTimeMillis = 1_000L,
                attemptKind = "Review",
                attemptedAtEpochMillis = 1_500L
            )
        )

        val nextDay = reviewRepository.getNextDueCard(
            nowEpochMillis = DAY + 1_000L,
            dayStartEpochMillis = DAY,
            dailyReviewLimit = 1,
            excludedWordId = null
        )

        assertNotNull(nextDay)
    }

    @Test
    fun forgottenMatureWordReturnsToLearningAndLowersProficiency() = runTest {
        val bookId = vocabularyRepository.createBook("Mature", "")
        val wordId = createReviewWord(bookId, "durable", "持久的")
        database.reviewDao().getStatesForWord(wordId).forEach { state ->
            database.reviewDao().updateState(
                state.copy(
                    status = "Mastered",
                    dueAtEpochMillis = 1_000L,
                    intervalDays = 60.0
                )
            )
        }
        database.vocabularyDao().setReviewProgress(wordId, "Mature", 1.0, 1_000L)
        val item = requireNotNull(
            reviewRepository.getNextDueCard(2_000L, 0L, 100, null)
        )

        reviewRepository.recordReview(
            item = item,
            rating = ReviewRating.Again,
            answerResult = ReviewAnswerResult(null, null),
            responseTimeMillis = 1_000L,
            reviewedAtEpochMillis = 3_000L,
            sessionId = "mature-return"
        )

        val word = requireNotNull(vocabularyRepository.getWord(wordId))
        assertEquals("Learning", word.intakeStatus)
        assertEquals(0.25, word.proficiencyScore, 0.0)
        assertEquals(
            "Relearning",
            database.reviewDao().getStateForCard(item.cardId)?.status
        )
    }

    private suspend fun createReviewWord(
        bookId: Long,
        word: String,
        meaning: String
    ): Long {
        val wordId = vocabularyRepository.createWord(
            WordInput(bookId = bookId, word = word, meaning = meaning)
        )
        database.reviewDao().getStatesForWord(wordId).forEach { state ->
            database.reviewDao().updateState(
                state.copy(
                    status = "Review",
                    dueAtEpochMillis = 1_000L,
                    intervalDays = 2.0
                )
            )
        }
        return wordId
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1_000L
    }
}
