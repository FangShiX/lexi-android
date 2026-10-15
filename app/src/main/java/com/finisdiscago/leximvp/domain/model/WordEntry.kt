package com.finisdiscago.leximvp.domain.model

data class WordEntry(
    val id: Long,
    val bookId: Long,
    val word: String,
    val normalizedWord: String,
    val meaning: String,
    val concept: String? = null,
    val pronunciation: String? = null,
    val exampleSentence: String? = null,
    val exampleTranslation: String? = null,
    val synonyms: String? = null,
    val tags: List<String> = emptyList(),
    val richContentJson: String? = null,
    val sourceOrder: Int = 0,
    val intakeStatus: String = "Queued",
    val manualFamiliarity: String? = null,
    val proficiencyScore: Double = 0.0,
    val lastSeenAtEpochMillis: Long? = null
)
