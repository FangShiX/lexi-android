package com.finisdiscago.leximvp.domain.model

data class ReviewCard(
    val id: Long,
    val wordId: Long,
    val type: CardType,
    val enabled: Boolean = true
)

enum class CardType {
    ForeignToMeaning,
    MeaningToForeign,
    AudioToSpelling,
    AudioToChoice,
    SentenceCloze,
    SynonymDiscrimination
}
