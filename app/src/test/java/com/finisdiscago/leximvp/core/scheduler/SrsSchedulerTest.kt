package com.finisdiscago.leximvp.core.scheduler

import com.finisdiscago.leximvp.domain.model.ReviewState
import com.finisdiscago.leximvp.domain.model.ReviewStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SrsSchedulerTest {
    private val reviewedAt = Instant.ofEpochMilli(1_000L)
    private val state = ReviewState(
        id = 1,
        wordId = 2,
        cardId = 3,
        status = ReviewStatus.Review,
        dueAt = reviewedAt,
        intervalDays = 4.0,
        easeFactor = 2.5,
        repetitionCount = 2,
        lapseCount = 0
    )

    @Test
    fun againSchedulesTenMinuteRelearningStep() {
        val result = SrsScheduler.schedule(state, ReviewRating.Again, reviewedAt)

        assertEquals(ReviewStatus.Relearning, result.status)
        assertEquals(601_000L, result.dueAt.toEpochMilli())
        assertEquals(0.0, result.intervalDays, 0.0)
        assertEquals(1, result.lapseCount)
    }

    @Test
    fun goodUsesEaseFactor() {
        val result = SrsScheduler.schedule(state, ReviewRating.Good, reviewedAt)

        assertEquals(ReviewStatus.Review, result.status)
        assertEquals(10.0, result.intervalDays, 0.0)
        assertEquals(864_001_000L, result.dueAt.toEpochMilli())
    }

    @Test
    fun easyIncreasesEaseAndInterval() {
        val result = SrsScheduler.schedule(state, ReviewRating.Easy, reviewedAt)

        assertEquals(15.0, result.intervalDays, 0.0)
        assertEquals(2.65, result.easeFactor, 0.0)
    }

    @Test
    fun longIntervalBecomesMasteredWithBoundedDeterministicReturn() {
        val matureState = state.copy(intervalDays = 20.0, repetitionCount = 5)

        val first = SrsScheduler.schedule(matureState, ReviewRating.Good, reviewedAt)
        val second = SrsScheduler.schedule(matureState, ReviewRating.Good, reviewedAt)

        assertEquals(ReviewStatus.Mastered, first.status)
        assertEquals(first.intervalDays, second.intervalDays, 0.0)
        assertTrue(first.intervalDays in 30.0..365.0)
    }
}
