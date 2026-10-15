package com.finisdiscago.leximvp.domain.model

data class VocabularyBook(
    val id: Long,
    val name: String,
    val description: String,
    val wordCount: Int,
    val learnedWordCount: Int = 0,
    val bookType: String = "User",
    val isProtected: Boolean = false,
    val selectionEnabled: Boolean = true,
    val dailySelectionLimit: Int = 20,
    val selectionPriority: Int = 0,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
) {
    val pendingWordCount: Int get() = (wordCount - learnedWordCount).coerceAtLeast(0)
    val progress: Float
        get() = if (wordCount == 0) 0f else learnedWordCount.toFloat() / wordCount
}
