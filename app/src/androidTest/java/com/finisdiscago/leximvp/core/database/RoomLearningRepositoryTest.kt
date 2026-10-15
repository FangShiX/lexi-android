package com.finisdiscago.leximvp.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.finisdiscago.leximvp.domain.model.LearningRating
import com.finisdiscago.leximvp.domain.repository.WordInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomLearningRepositoryTest {
    private lateinit var database: LexiDatabase
    private lateinit var vocabularyRepository: RoomVocabularyRepository
    private lateinit var learningRepository: RoomLearningRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            LexiDatabase::class.java
        ).allowMainThreadQueries().build()
        vocabularyRepository = RoomVocabularyRepository(database) { 1_000L }
        learningRepository = RoomLearningRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun firstRatingMovesCanonicalCardBackIntoTodayQueue() = runTest {
        val item = createLearningItem()

        learningRepository.recordRating(
            item = item,
            rating = LearningRating.Forgot,
            responseTimeMillis = 2_000L,
            reviewedAtEpochMillis = 10_000L,
            dayStartEpochMillis = 0L,
            sessionId = "session"
        )

        val state = database.reviewDao().getStateForCard(item.cardId)
        assertEquals("Learning", state?.status)
        assertEquals(310_000L, state?.dueAtEpochMillis)
        assertEquals(1, database.reviewDao().getWordAttempts(item.wordId, 0, 20_000L).size)
    }

    @Test
    fun thirdRatingGraduatesEveryCardAfterLastTwoAreKnown() = runTest {
        val item = createLearningItem()
        val ratings = listOf(
            LearningRating.Fuzzy,
            LearningRating.Know,
            LearningRating.Know
        )

        ratings.forEachIndexed { index, rating ->
            learningRepository.recordRating(
                item = item,
                rating = rating,
                responseTimeMillis = 1_000L,
                reviewedAtEpochMillis = 10_000L + index * 1_000L,
                dayStartEpochMillis = 0L,
                sessionId = "session"
            )
        }

        val states = database.reviewDao().getStatesForWord(item.wordId)
        assertEquals(3, states.size)
        assertTrue(states.all { it.status == "Review" })
        assertTrue(states.all { it.intervalDays == 2.0 })
        assertTrue(states.all { it.repetitionCount == 1 })
    }

    @Test
    fun dailyLimitBlocksStartingAnotherWordButAllowsStartedWordToContinue() = runTest {
        val bookId = vocabularyRepository.createBook("Limited", "")
        vocabularyRepository.createWord(WordInput(bookId, "first", "第一"))
        vocabularyRepository.createWord(WordInput(bookId, "second", "第二"))
        val first = requireNotNull(
            learningRepository.getNextDueWord(1_000L, 0L, 1)
        )
        learningRepository.recordRating(
            item = first,
            rating = LearningRating.Forgot,
            responseTimeMillis = 1_000L,
            reviewedAtEpochMillis = 2_000L,
            dayStartEpochMillis = 0L,
            sessionId = "limited"
        )

        val beforeRepeat = learningRepository.getNextDueWord(100_000L, 0L, 1)
        val repeat = learningRepository.getNextDueWord(302_000L, 0L, 1)

        assertEquals(null, beforeRepeat)
        assertEquals(first.wordId, repeat?.wordId)
    }

    @Test
    fun dailyNewWordLimitResetsAtNextDayBoundary() = runTest {
        val bookId = vocabularyRepository.createBook("Daily reset", "")
        vocabularyRepository.createWord(WordInput(bookId, "first", "first"))
        vocabularyRepository.createWord(WordInput(bookId, "second", "second"))
        val first = requireNotNull(learningRepository.getNextDueWord(1_000L, 0L, 1))
        learningRepository.recordRating(
            item = first,
            rating = LearningRating.Forgot,
            responseTimeMillis = 1_000L,
            reviewedAtEpochMillis = 2_000L,
            dayStartEpochMillis = 0L,
            sessionId = "day-one"
        )

        val sameDay = learningRepository.getNextDueWord(100_000L, 0L, 1)
        val nextDay = learningRepository.getNextDueWord(DAY + 1_000L, DAY, 1)

        assertEquals(null, sameDay)
        assertNotNull(nextDay)
    }

    @Test
    fun dailyPlannerUsesBookPriorityAndIsIdempotent() = runTest {
        val lowPriorityBook = vocabularyRepository.createBook("Low", "")
        val highPriorityBook = vocabularyRepository.createBook("High", "")
        val lowWord = vocabularyRepository.createWord(
            WordInput(lowPriorityBook, "lower", "低")
        )
        val highWord = vocabularyRepository.createWord(
            WordInput(highPriorityBook, "higher", "高")
        )
        database.vocabularyDao().updateIntakeStatus(
            listOf(lowWord, highWord),
            "Pending",
            1_000L
        )
        vocabularyRepository.updateBookPlan(lowPriorityBook, true, 1, 0)
        vocabularyRepository.updateBookPlan(highPriorityBook, true, 1, 10)
        val planner = DailyStudyPlanner(database) { 2_000L }

        val first = planner.ensurePlan("2026-06-14", globalLimit = 1)
        val second = planner.ensurePlan("2026-06-14", globalLimit = 1)
        val assignments = database.studyPlanDao()
            .observeAssignments("2026-06-14")
            .first()

        assertEquals(1, first.assignedCount)
        assertEquals(0, second.assignedCount)
        assertEquals(listOf(highWord), assignments.map { it.wordId })
        assertEquals(
            "Pending",
            database.vocabularyDao().getWord(lowWord)?.intakeStatus
        )
        assertEquals(
            "Queued",
            database.vocabularyDao().getWord(highWord)?.intakeStatus
        )
    }

    private suspend fun createLearningItem() =
        vocabularyRepository.createBook("Core", "").let { bookId ->
            vocabularyRepository.createWord(
                WordInput(
                    bookId = bookId,
                    word = "excess",
                    meaning = "过量"
                )
            )
            learningRepository.getNextDueWord(
                nowEpochMillis = 1_000L,
                dayStartEpochMillis = 0L,
                dailyNewWordLimit = 20
            ).also(::assertNotNull)!!
        }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1_000L
    }
}
