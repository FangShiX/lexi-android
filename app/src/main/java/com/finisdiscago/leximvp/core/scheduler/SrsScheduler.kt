package com.finisdiscago.leximvp.core.scheduler

import com.finisdiscago.leximvp.domain.model.ReviewState
import com.finisdiscago.leximvp.domain.model.ReviewStatus
import java.time.Instant
import java.time.Duration
import kotlin.math.max
import kotlin.math.roundToLong

object SrsScheduler {
    fun schedule(
        state: ReviewState,
        rating: ReviewRating,
        reviewedAt: Instant
    ): ReviewState {
        val baseInterval = when (rating) {
            ReviewRating.Again -> 0.0
            ReviewRating.Hard -> max(1.0, state.intervalDays * 1.2)
            ReviewRating.Good -> max(1.0, state.intervalDays * state.easeFactor)
            ReviewRating.Easy -> max(2.0, state.intervalDays * state.easeFactor * 1.5)
        }
        val nextInterval = if (baseInterval >= MATURE_INTERVAL_DAYS) {
            deterministicMatureInterval(baseInterval, state.cardId, state.repetitionCount)
        } else {
            baseInterval
        }
        val nextEase = when (rating) {
            ReviewRating.Again -> max(1.3, state.easeFactor - 0.2)
            ReviewRating.Hard -> max(1.3, state.easeFactor - 0.05)
            ReviewRating.Good -> state.easeFactor
            ReviewRating.Easy -> state.easeFactor + 0.15
        }

        return state.copy(
            status = when {
                rating == ReviewRating.Again -> ReviewStatus.Relearning
                nextInterval >= MATURE_INTERVAL_DAYS -> ReviewStatus.Mastered
                else -> ReviewStatus.Review
            },
            dueAt = if (rating == ReviewRating.Again) {
                reviewedAt.plus(Duration.ofMinutes(10))
            } else {
                reviewedAt.plusMillis((nextInterval * DAY_MILLIS).roundToLong())
            },
            intervalDays = nextInterval,
            easeFactor = nextEase,
            repetitionCount = state.repetitionCount + 1,
            lapseCount = state.lapseCount + if (rating == ReviewRating.Again) 1 else 0
        )
    }

    private fun deterministicMatureInterval(
        intervalDays: Double,
        cardId: Long,
        repetitionCount: Int
    ): Double {
        val hash = (cardId * 31 + repetitionCount * 17).hashCode()
        val jitter = ((hash and 0x7fffffff) % 21 - 10) / 100.0
        return (intervalDays * (1.0 + jitter)).coerceIn(
            MATURE_INTERVAL_DAYS,
            MAX_INTERVAL_DAYS
        )
    }

    private const val MATURE_INTERVAL_DAYS = 30.0
    private const val MAX_INTERVAL_DAYS = 365.0
    private const val DAY_MILLIS = 24 * 60 * 60 * 1_000L
}

enum class ReviewRating {
    Again,
    Hard,
    Good,
    Easy
}
