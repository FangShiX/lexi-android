package com.finisdiscago.leximvp.domain.model

data class ReviewQueueItem(
    val cardId: Long,
    val wordId: Long,
    val cardType: CardType,
    val word: String,
    val meaning: String,
    val pronunciation: String?,
    val exampleSentence: String?,
    val exampleTranslation: String?,
    val richContent: RichWordContent? = null,
    val matchedSense: RichWordSense? = null,
    val dueAtEpochMillis: Long,
    val intervalDays: Double,
    val exercise: com.finisdiscago.leximvp.core.text.ExerciseDefinition? = null,
    val easeFactor: Double
)

data class ReviewAnswerResult(
    val isCorrect: Boolean?,
    val normalizedAnswer: String?
)
