package com.finisdiscago.leximvp.core.database

import androidx.room.withTransaction

/** Keeps lexical content reversible without erasing review state or attempt history. */
class LexicalContentLifecycleService(private val database: LexiDatabase) {
    suspend fun setSenseActive(senseId: String, active: Boolean) {
        database.withTransaction {
            database.vocabularyDao().setSenseActive(senseId, active)
            database.vocabularyDao().setExamplesActiveForSense(senseId, active)
            database.reviewDao().setCardsEnabledForSense(senseId, active)
        }
    }
}
