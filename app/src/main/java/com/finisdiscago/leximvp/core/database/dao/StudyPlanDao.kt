package com.finisdiscago.leximvp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.finisdiscago.leximvp.core.database.entity.DailyStudyAssignmentEntity
import com.finisdiscago.leximvp.core.database.entity.VocabularyBookEntity
import kotlinx.coroutines.flow.Flow

data class PendingStudyWord(
    val id: Long,
    val bookId: Long
)

data class SelectionBookCapacity(
    val id: Long,
    val dailySelectionLimit: Int,
    val selectionPriority: Int,
    val createdOrder: Long,
    val pendingCount: Int
)

@Dao
interface StudyPlanDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAssignments(
        assignments: List<DailyStudyAssignmentEntity>
    ): List<Long>

    @Query(
        """
        SELECT * FROM daily_study_assignments
        WHERE localDate = :localDate
        ORDER BY assignedAtEpochMillis, id
        """
    )
    fun observeAssignments(localDate: String): Flow<List<DailyStudyAssignmentEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM daily_study_assignments
        WHERE localDate = :localDate AND bookId = :bookId
        """
    )
    suspend fun countAssignments(localDate: String, bookId: Long): Int

    @Query("SELECT COUNT(*) FROM daily_study_assignments WHERE localDate = :localDate")
    suspend fun countAssignments(localDate: String): Int

    @Query(
        """
        SELECT * FROM vocabulary_books
        WHERE selectionEnabled = 1
        ORDER BY selectionPriority DESC, createdOrder ASC, id ASC
        """
    )
    suspend fun getSelectionBooks(): List<VocabularyBookEntity>

    @Query(
        """
        SELECT id, bookId FROM word_entries
        WHERE bookId = :bookId AND intakeStatus = 'Pending' AND meaning != ''
        ORDER BY sourceOrder ASC, id ASC
        LIMIT :limit
        """
    )
    suspend fun getPendingWords(bookId: Long, limit: Int): List<PendingStudyWord>

    @Query(
        """
        SELECT
            vocabulary_books.id AS id,
            vocabulary_books.dailySelectionLimit AS dailySelectionLimit,
            vocabulary_books.selectionPriority AS selectionPriority,
            vocabulary_books.createdOrder AS createdOrder,
            COUNT(word_entries.id) AS pendingCount
        FROM vocabulary_books
        LEFT JOIN word_entries
            ON word_entries.bookId = vocabulary_books.id
            AND word_entries.intakeStatus = 'Pending'
        WHERE vocabulary_books.selectionEnabled = 1
        GROUP BY vocabulary_books.id
        ORDER BY vocabulary_books.selectionPriority DESC,
            vocabulary_books.createdOrder ASC,
            vocabulary_books.id ASC
        """
    )
    suspend fun getSelectionCapacity(): List<SelectionBookCapacity>

    @Query("DELETE FROM daily_study_assignments WHERE localDate < :oldestLocalDate")
    suspend fun deleteBefore(oldestLocalDate: String)
}
