package com.finisdiscago.leximvp.core.database

import com.finisdiscago.leximvp.core.database.entity.ReviewCardEntity
import com.finisdiscago.leximvp.core.database.entity.ReviewStateEntity
import com.finisdiscago.leximvp.domain.model.CardType

class StudyCardFactory(private val database: LexiDatabase) {
    suspend fun ensure(wordId: Long, timestamp: Long) {
        val word = database.vocabularyDao().getWord(wordId) ?: return
        val senses = database.vocabularyDao().getSensesForWord(wordId).filter { it.active }
        if (word.meaning.isBlank() && senses.none { it.meaningZh.isNotBlank() || it.definitionEn.isNotBlank() }) return
        val existing = database.reviewDao().getCardsForWord(wordId).map { it.stableId }.toSet()
        val cards = mutableListOf(
            ReviewCardEntity(stableId = "${word.stableId}:card:${CardType.ForeignToMeaning.name}", wordId = wordId, cardType = CardType.ForeignToMeaning.name, createdAtEpochMillis = timestamp),
            ReviewCardEntity(stableId = "${word.stableId}:card:${CardType.AudioToSpelling.name}", wordId = wordId, cardType = CardType.AudioToSpelling.name, createdAtEpochMillis = timestamp)
        )
        if (senses.isEmpty()) cards += ReviewCardEntity(
            stableId = "${word.stableId}:card:${CardType.MeaningToForeign.name}", wordId = wordId,
            cardType = CardType.MeaningToForeign.name, createdAtEpochMillis = timestamp
        )
        senses.filter { it.meaningZh.isNotBlank() || it.definitionEn.isNotBlank() }.forEach { sense ->
            listOf(CardType.ForeignToMeaning, CardType.MeaningToForeign).forEach { type ->
                cards += ReviewCardEntity(
                    stableId = "${sense.stableId}:card:${type.name}", wordId = wordId, cardType = type.name,
                    senseId = sense.stableId, createdAtEpochMillis = timestamp
                )
            }
            database.vocabularyDao().getExamplesForSense(sense.stableId).filter { it.active && it.text.isNotBlank() }.forEach { example ->
                val containsHeadword = Regex("(?i)(?<![\\p{L}])${Regex.escape(word.word)}(?![\\p{L}])").containsMatchIn(example.text)
                if (containsHeadword) cards += ReviewCardEntity(
                    stableId = "${example.stableId}:card:${CardType.SentenceCloze.name}", wordId = wordId,
                    cardType = CardType.SentenceCloze.name, senseId = sense.stableId, exampleId = example.stableId,
                    createdAtEpochMillis = timestamp
                )
            }
        }
        com.finisdiscago.leximvp.core.text.ExerciseCapabilityResolver.exercises(word.word, word.richContentJson)
            .filter { it.type in setOf(CardType.AudioToChoice, CardType.SynonymDiscrimination) }
            .forEach { exercise -> cards += ReviewCardEntity(
                stableId = "${word.stableId}:exercise:${exercise.id}", wordId = wordId,
                cardType = exercise.type.name, createdAtEpochMillis = timestamp
            ) }
        val ids = database.reviewDao().insertCards(cards.distinctBy { it.stableId }.filter { it.stableId !in existing })
        val alreadyReviewing = word.intakeStatus in setOf("Reviewing", "Mature")
        database.reviewDao().upsertStates(ids.map {
            ReviewStateEntity(cardId = it, status = if (alreadyReviewing) "Review" else "New", dueAtEpochMillis = timestamp)
        })
    }
}
