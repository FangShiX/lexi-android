package com.finisdiscago.leximvp.core.database.dao

import androidx.room.*
import com.finisdiscago.leximvp.core.database.entity.WorkspaceRecordEntity
import kotlinx.coroutines.flow.Flow

data class LexiconRow(val id: Long, val word: String, val meaning: String, val intakeStatus: String,
    val proficiencyScore: Double, val bookId: Long, val richContentJson: String?,
    val frequencyRank: Long?, val frequencyPerMillion: Double?, val frequencyCorpus: String?)
data class LexiconTagOption(val category: String, val value: String, val count: Int)

@Dao
interface LexiconDao {
    @Query("""SELECT id, word, meaning, intakeStatus, proficiencyScore, bookId, richContentJson,
        (SELECT MIN(fr.rank) FROM frequency_records fr WHERE fr.wordId = word_entries.id) AS frequencyRank,
        (SELECT MAX(fr.perMillion) FROM frequency_records fr WHERE fr.wordId = word_entries.id) AS frequencyPerMillion,
        (SELECT fr.corpusName FROM frequency_records fr WHERE fr.wordId = word_entries.id ORDER BY fr.rank IS NULL, fr.rank, fr.perMillion DESC LIMIT 1) AS frequencyCorpus
        FROM word_entries
        WHERE (:query = '' OR normalizedWord LIKE :query || '%' OR meaning LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%')
        AND (:status = '' OR intakeStatus = :status)
        AND (:sourceId = 0 OR EXISTS (SELECT 1 FROM entry_sources es WHERE es.wordId = word_entries.id AND es.sourceId = :sourceId))
        AND (:tagCategory = '' OR EXISTS (SELECT 1 FROM entry_tags et WHERE et.wordId = word_entries.id AND et.category = :tagCategory AND (:tagValue = '' OR et.value = :tagValue)))
        ORDER BY CASE WHEN :ordering = 'alphabet' THEN normalizedWord END ASC,
        CASE WHEN :ordering = 'familiarity' THEN proficiencyScore END ASC,
        CASE WHEN :ordering = 'corpus_frequency' THEN NOT EXISTS (SELECT 1 FROM frequency_records fr WHERE fr.wordId = word_entries.id) END ASC,
        CASE WHEN :ordering = 'corpus_frequency' THEN (SELECT MIN(fr.rank) FROM frequency_records fr WHERE fr.wordId = word_entries.id) END ASC,
        CASE WHEN :ordering = 'corpus_frequency' THEN (SELECT MAX(fr.perMillion) FROM frequency_records fr WHERE fr.wordId = word_entries.id) END DESC,
        CASE WHEN :sourceId != 0 THEN (SELECT es.sourceOrder FROM entry_sources es WHERE es.wordId = word_entries.id AND es.sourceId = :sourceId) END,
        sourceOrder, id
        LIMIT :limit OFFSET :offset""")
    fun page(query: String, status: String, sourceId: Long, tagCategory: String, tagValue: String, ordering: String, limit: Int, offset: Int): Flow<List<LexiconRow>>

    @Query("SELECT category, value, COUNT(DISTINCT wordId) AS count FROM entry_tags GROUP BY category, value ORDER BY category, count DESC, value LIMIT 40")
    fun tagOptions(): Flow<List<LexiconTagOption>>

    @Query("SELECT COUNT(*) FROM frequency_records")
    fun frequencyRecordCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM word_entries")
    fun count(): Flow<Int>

    @Query("SELECT * FROM workspace_records WHERE kind = :kind ORDER BY updatedAtEpochMillis DESC")
    fun records(kind: String): Flow<List<WorkspaceRecordEntity>>

    @Query("SELECT * FROM workspace_records WHERE id = :id")
    suspend fun record(id: Long): WorkspaceRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(record: WorkspaceRecordEntity): Long

    @Query("DELETE FROM workspace_records WHERE id = :id")
    suspend fun deleteRecord(id: Long)

    @Query("SELECT * FROM workspace_records ORDER BY id")
    suspend fun allRecords(): List<WorkspaceRecordEntity>
}
