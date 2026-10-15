package com.finisdiscago.leximvp.domain.repository

import com.finisdiscago.leximvp.core.scheduler.ReviewRating
import com.finisdiscago.leximvp.domain.model.ReviewAnswerResult
import com.finisdiscago.leximvp.domain.model.ReviewQueueItem

interface ReviewRepository {
    suspend fun getNextDueCard(
        nowEpochMillis: Long,
        dayStartEpochMillis: Long,
        dailyReviewLimit: Int,
        excludedWordId: Long? = null
    ): ReviewQueueItem?

    suspend fun countDueCards(
        nowEpochMillis: Long,
        dayStartEpochMillis: Long,
        dailyReviewLimit: Int
    ): Int

    fun checkAnswer(item: ReviewQueueItem, answer: String): ReviewAnswerResult

    suspend fun recordReview(
        item: ReviewQueueItem,
        rating: ReviewRating,
        answerResult: ReviewAnswerResult,
        responseTimeMillis: Long,
        reviewedAtEpochMillis: Long,
        sessionId: String
    )
}
