package com.finisdiscago.leximvp.core.scheduler

import com.finisdiscago.leximvp.domain.model.LearningRating
import kotlin.math.roundToLong

data class IntraDaySchedule(
    val graduated: Boolean,
    val nextDueAtEpochMillis: Long,
    val initialIntervalDays: Double = 0.0
)

object IntraDayLearningScheduler {
    const val REQUIRED_ATTEMPTS = 3

    fun schedule(
        ratings: List<LearningRating>,
        reviewedAtEpochMillis: Long
    ): IntraDaySchedule {
        require(ratings.isNotEmpty())

        if (ratings.size < REQUIRED_ATTEMPTS || ratings.takeLast(2).any { it.score < LearningRating.Know.score }) {
            val delayMinutes = when (ratings.last()) {
                LearningRating.Forgot -> 5L
                LearningRating.Fuzzy -> 15L
                LearningRating.Know -> 30L
                LearningRating.Mastered -> 60L
            }
            return IntraDaySchedule(
                graduated = false,
                nextDueAtEpochMillis = reviewedAtEpochMillis + delayMinutes * 60_000L
            )
        }

        val average = ratings.map { it.score }.average()
        val intervalDays = when {
            average < 1.0 -> 1.0
            average < 2.0 -> 2.0
            else -> 4.0
        }
        return IntraDaySchedule(
            graduated = true,
            nextDueAtEpochMillis = reviewedAtEpochMillis +
                (intervalDays * 24 * 60 * 60 * 1_000).roundToLong(),
            initialIntervalDays = intervalDays
        )
    }
}
