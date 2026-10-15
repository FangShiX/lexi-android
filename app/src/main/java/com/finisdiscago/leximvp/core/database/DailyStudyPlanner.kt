package com.finisdiscago.leximvp.core.database

import androidx.room.withTransaction
import com.finisdiscago.leximvp.core.database.entity.DailyStudyAssignmentEntity
import com.finisdiscago.leximvp.core.database.entity.WordIntakeStatus

data class DailyStudyPlanResult(
    val localDate: String,
    val assignedCount: Int,
    val totalAssignedCount: Int
)

class DailyStudyPlanner(
    private val database: LexiDatabase,
    private val now: () -> Long = System::currentTimeMillis
) {
    suspend fun ensurePlan(
        localDate: String,
        globalLimit: Int
    ): DailyStudyPlanResult = database.withTransaction {
        val planDao = database.studyPlanDao()
        val vocabularyDao = database.vocabularyDao()
        val safeGlobalLimit = globalLimit.coerceIn(0, 200)
        var totalAssigned = planDao.countAssignments(localDate)
        var newlyAssigned = 0

        if (totalAssigned < safeGlobalLimit) {
            for (book in planDao.getSelectionBooks()) {
                val existingForBook = planDao.countAssignments(localDate, book.id)
                val availableForBook =
                    (book.dailySelectionLimit - existingForBook).coerceAtLeast(0)
                val availableGlobally = safeGlobalLimit - totalAssigned
                val take = minOf(availableForBook, availableGlobally)
                if (take <= 0) continue

                val candidates = planDao.getPendingWords(book.id, take)
                if (candidates.isEmpty()) continue
                val timestamp = now()
                val insertedIds = planDao.insertAssignments(
                    candidates.map { candidate ->
                        DailyStudyAssignmentEntity(
                            localDate = localDate,
                            wordId = candidate.id,
                            bookId = candidate.bookId,
                            assignedAtEpochMillis = timestamp
                        )
                    }
                )
                val insertedWordIds = candidates.zip(insertedIds)
                    .filter { (_, assignmentId) -> assignmentId != -1L }
                    .map { (candidate, _) -> candidate.id }
                if (insertedWordIds.isNotEmpty()) {
                    insertedWordIds.forEach { wordId -> StudyCardFactory(database).ensure(wordId, timestamp) }
                    vocabularyDao.updateIntakeStatus(
                        wordIds = insertedWordIds,
                        status = WordIntakeStatus.QUEUED,
                        timestamp = timestamp
                    )
                    newlyAssigned += insertedWordIds.size
                    totalAssigned += insertedWordIds.size
                }
                if (totalAssigned >= safeGlobalLimit) break
            }
        }

        DailyStudyPlanResult(localDate, newlyAssigned, totalAssigned)
    }
}
