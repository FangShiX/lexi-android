package com.finisdiscago.leximvp.domain.model

import java.time.Instant

data class ReviewState(
    val id: Long,
    val wordId: Long,
    val cardId: Long,
    val status: ReviewStatus,
    val dueAt: Instant,
    val intervalDays: Double,
    val easeFactor: Double,
    val repetitionCount: Int,
    val lapseCount: Int
)

enum class ReviewStatus {
    New,
    Learning,
    Review,
    Relearning,
    Mastered,
    Suspended
}
