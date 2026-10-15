package com.finisdiscago.leximvp.domain.model

data class LearningQueueItem(
    val wordId: Long,
    val cardId: Long,
    val word: String,
    val meaning: String,
    val pronunciation: String?,
    val exampleSentence: String?,
    val exampleTranslation: String?,
    val richContent: RichWordContent? = null,
    val matchedSense: RichWordSense? = null,
    val dueAtEpochMillis: Long,
    val attemptsToday: Int
)
