package com.finisdiscago.leximvp.core.database

import com.finisdiscago.leximvp.core.database.entity.LearningAttemptEntity

class WordAssessmentService(private val database: LexiDatabase) {
    suspend fun recompute(wordId: Long, timestamp: Long, clearManual: Boolean = false) {
        val cards = database.reviewDao().getCardsForWord(wordId).filter { it.enabled }.map { it.id }.toSet()
        val states = database.reviewDao().getStatesForWord(wordId).filter { it.cardId in cards }
        val score = states.minOfOrNull {
            when (it.status) {
                "Mastered" -> 1.0
                "Review" -> 0.6
                "Relearning", "Learning" -> 0.25
                else -> 0.0
            }
        } ?: 0.0
        val status = when {
            score >= 1.0 -> "Mature"
            score >= 0.6 -> "Reviewing"
            score > 0 -> "Learning"
            else -> "Queued"
        }
        if (clearManual) {
            val previous = database.vocabularyDao().getWord(wordId)?.manualFamiliarity
            database.vocabularyDao().updateWordFamiliarity(wordId, null, timestamp)
            if (previous != null) database.reviewDao().insertAttempt(
                LearningAttemptEntity(
                    sessionId = "familiarity-downgrade:$timestamp", wordId = wordId, rating = 0,
                    isCorrect = false, responseTimeMillis = 0, attemptKind = "FamiliarityDowngrade",
                    attemptedAtEpochMillis = timestamp
                )
            )
        }
        database.vocabularyDao().setReviewProgress(wordId, status, score, timestamp)
    }
}
