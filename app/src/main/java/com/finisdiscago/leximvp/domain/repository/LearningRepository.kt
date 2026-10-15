package com.finisdiscago.leximvp.domain.repository

import com.finisdiscago.leximvp.domain.model.LearningQueueItem
import com.finisdiscago.leximvp.domain.model.LearningRating

interface LearningRepository {
    fun selectWords(ids: List<Long>) {}

    suspend fun getNextDueWord(
        nowEpochMillis: Long,
        dayStartEpochMillis: Long,
        dailyNewWordLimit: Int
    ): LearningQueueItem?

    suspend fun countDueWords(
        nowEpochMillis: Long,
        dayStartEpochMillis: Long,
        dailyNewWordLimit: Int
    ): Int

    suspend fun countAllDueWords(
        nowEpochMillis: Long,
        dayStartEpochMillis: Long,
        dailyNewWordLimit: Int
    ): Int = countDueWords(nowEpochMillis, dayStartEpochMillis, dailyNewWordLimit)

    suspend fun recordRating(
        item: LearningQueueItem,
        rating: LearningRating,
        responseTimeMillis: Long,
        reviewedAtEpochMillis: Long,
        dayStartEpochMillis: Long,
        sessionId: String
    )
}
