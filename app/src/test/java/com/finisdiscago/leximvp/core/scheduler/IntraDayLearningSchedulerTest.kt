package com.finisdiscago.leximvp.core.scheduler

import com.finisdiscago.leximvp.domain.model.LearningRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntraDayLearningSchedulerTest {
    @Test
    fun schedulesForgottenWordFiveMinutesLater() {
        val result = IntraDayLearningScheduler.schedule(
            ratings = listOf(LearningRating.Forgot),
            reviewedAtEpochMillis = 1_000L
        )

        assertFalse(result.graduated)
        assertEquals(301_000L, result.nextDueAtEpochMillis)
    }

    @Test
    fun schedulesKnownWordThirtyMinutesLater() {
        val result = IntraDayLearningScheduler.schedule(
            ratings = listOf(LearningRating.Know),
            reviewedAtEpochMillis = 1_000L
        )

        assertFalse(result.graduated)
        assertEquals(1_801_000L, result.nextDueAtEpochMillis)
    }

    @Test
    fun graduatesAfterThreeAttemptsUsingAverageRating() {
        val result = IntraDayLearningScheduler.schedule(
            ratings = listOf(
                LearningRating.Forgot,
                LearningRating.Know,
                LearningRating.Know
            ),
            reviewedAtEpochMillis = 1_000L
        )

        assertTrue(result.graduated)
        assertEquals(2.0, result.initialIntervalDays, 0.0)
        assertEquals(172_801_000L, result.nextDueAtEpochMillis)
    }

    @Test
    fun repeatedFailuresNeverGraduate() {
        assertFalse(IntraDayLearningScheduler.schedule(List(5) { LearningRating.Forgot }, 0).graduated)
        assertFalse(IntraDayLearningScheduler.schedule(listOf(LearningRating.Know, LearningRating.Know, LearningRating.Forgot), 0).graduated)
    }

    @Test
    fun strongPerformanceStartsAtFourDays() {
        val result = IntraDayLearningScheduler.schedule(
            ratings = listOf(
                LearningRating.Know,
                LearningRating.Mastered,
                LearningRating.Mastered
            ),
            reviewedAtEpochMillis = 1_000L
        )

        assertTrue(result.graduated)
        assertEquals(4.0, result.initialIntervalDays, 0.0)
    }
}
