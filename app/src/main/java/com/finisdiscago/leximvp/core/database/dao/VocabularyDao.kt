package com.finisdiscago.leximvp.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.finisdiscago.leximvp.core.database.entity.VocabularyBookEntity
import com.finisdiscago.leximvp.core.database.entity.WordEntryEntity
import com.finisdiscago.leximvp.core.database.entity.WordFormEntity
import com.finisdiscago.leximvp.core.database.entity.SourceRecordEntity
import com.finisdiscago.leximvp.core.database.entity.EntrySourceEntity
import com.finisdiscago.leximvp.core.database.entity.SenseEntity
import com.finisdiscago.leximvp.core.database.entity.ExampleEntity
import com.finisdiscago.leximvp.core.database.entity.EntryRelationEntity
import com.finisdiscago.leximvp.core.database.entity.EntryTagEntity
import com.finisdiscago.leximvp.core.database.entity.FrequencyRecordEntity
import kotlinx.coroutines.flow.Flow

data class VocabularyBookSummary(
    val id: Long,
    val name: String,
    val description: String,
    val bookType: String,
    val isProtected: Boolean,
    val selectionEnabled: Boolean,
    val dailySelectionLimit: Int,
    val selectionPriority: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val wordCount: Int,
    val learnedWordCount: Int
)

data class WordLearningMatch(
    val id: Long,
    val word: String,
    val normalizedWord: String,
    val matchedText: String,
    val meaning: String,
    val pronunciation: String?,
    val learningStatus: String?
)

@Dao
interface VocabularyDao {
    @Query("SELECT * FROM vocabulary_books WHERE name = :name LIMIT 1")
    suspend fun getBookByName(name: String): VocabularyBookEntity?

    @Insert
    suspend fun insertBook(book: VocabularyBookEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSource(source: SourceRecordEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntrySources(sources: List<EntrySourceEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSenses(senses: List<SenseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExamples(examples: List<ExampleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRelations(relations: List<EntryRelationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntryTags(tags: List<EntryTagEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFrequencyRecords(records: List<FrequencyRecordEntity>)

    @Query("SELECT * FROM source_records WHERE id = :sourceId")
    suspend fun getSource(sourceId: Long): SourceRecordEntity?

    @Query("SELECT * FROM source_records WHERE stableId = :stableId LIMIT 1")
    suspend fun getSourceByStableId(stableId: String): SourceRecordEntity?

    @Query("DELETE FROM source_records WHERE id = :sourceId")
    suspend fun deleteSource(sourceId: Long)

    @Query("SELECT * FROM entry_sources WHERE wordId = :wordId ORDER BY sourceOrder, sourceId")
    suspend fun getSourcesForWord(wordId: Long): List<EntrySourceEntity>

    @Query("SELECT wordId FROM entry_sources WHERE sourceId = :sourceId AND wordId IN (:wordIds)")
    suspend fun findWordIdsInSource(sourceId: Long, wordIds: List<Long>): List<Long>

    @Update
    suspend fun updateBook(book: VocabularyBookEntity)

    @Delete
    suspend fun deleteBook(book: VocabularyBookEntity)

    @Query(
        """
        SELECT
            vocabulary_books.id AS id,
            vocabulary_books.name AS name,
            vocabulary_books.description AS description,
            vocabulary_books.bookType AS bookType,
            vocabulary_books.isProtected AS isProtected,
            vocabulary_books.selectionEnabled AS selectionEnabled,
            vocabulary_books.dailySelectionLimit AS dailySelectionLimit,
            vocabulary_books.selectionPriority AS selectionPriority,
            vocabulary_books.createdAtEpochMillis AS createdAtEpochMillis,
            vocabulary_books.updatedAtEpochMillis AS updatedAtEpochMillis,
            COUNT(word_entries.id) AS wordCount,
            COALESCE(SUM(
                CASE WHEN
                    word_entries.intakeStatus IN ('Learning', 'Reviewing', 'Mature')
                    OR word_entries.proficiencyScore > 0
                    OR word_entries.manualFamiliarity IN ('Familiar', 'Mastered')
                THEN 1 ELSE 0 END
            ), 0) AS learnedWordCount
        FROM vocabulary_books
        LEFT JOIN entry_sources ON entry_sources.sourceId = vocabulary_books.id
        LEFT JOIN word_entries ON word_entries.id = entry_sources.wordId
        GROUP BY vocabulary_books.id
        ORDER BY vocabulary_books.updatedAtEpochMillis DESC
        """
    )
    fun observeBooks(): Flow<List<VocabularyBookSummary>>

    @Query("SELECT * FROM vocabulary_books WHERE id = :bookId")
    suspend fun getBook(bookId: Long): VocabularyBookEntity?

    @Query("SELECT * FROM vocabulary_books WHERE bookType = :bookType LIMIT 1")
    suspend fun getBookByType(bookType: String): VocabularyBookEntity?

    @Query("SELECT COUNT(*) FROM entry_sources WHERE sourceId = :bookId")
    suspend fun getWordCount(bookId: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM word_entries
        INNER JOIN entry_sources ON entry_sources.wordId = word_entries.id
        WHERE entry_sources.sourceId = :bookId AND (
            intakeStatus IN ('Learning', 'Reviewing', 'Mature')
            OR proficiencyScore > 0
            OR manualFamiliarity IN ('Familiar', 'Mastered')
        )
        """
    )
    suspend fun getLearnedWordCount(bookId: Long): Int

    @Query("DELETE FROM vocabulary_books WHERE id = :bookId")
    suspend fun deleteBookById(bookId: Long)

    @Query("""
        UPDATE word_entries
        SET bookId = COALESCE(
            (SELECT es.sourceId FROM entry_sources es
             INNER JOIN vocabulary_books b ON b.id = es.sourceId
             WHERE es.wordId = word_entries.id AND es.sourceId != :removedSourceId
             ORDER BY es.sourceOrder, es.sourceId LIMIT 1),
            :fallbackBookId
        )
        WHERE bookId = :removedSourceId
    """)
    suspend fun rehomeOwnedWords(removedSourceId: Long, fallbackBookId: Long)

    @Insert
    suspend fun insertWord(word: WordEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWords(words: List<WordEntryEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWordForms(forms: List<WordFormEntity>): List<Long>

    @Query("SELECT * FROM word_forms WHERE wordId = :wordId ORDER BY id")
    suspend fun getWordForms(wordId: Long): List<WordFormEntity>

    @Query("SELECT * FROM senses WHERE wordId = :wordId ORDER BY displayOrder, stableId")
    suspend fun getSensesForWord(wordId: Long): List<SenseEntity>

    @Query("SELECT * FROM examples WHERE senseId = :senseId ORDER BY displayOrder, stableId")
    suspend fun getExamplesForSense(senseId: String): List<ExampleEntity>

    @Query("UPDATE senses SET active = :active WHERE stableId = :senseId")
    suspend fun setSenseActive(senseId: String, active: Boolean)

    @Query("UPDATE examples SET active = :active WHERE senseId = :senseId")
    suspend fun setExamplesActiveForSense(senseId: String, active: Boolean)

    @Update
    suspend fun updateWord(word: WordEntryEntity)

    @Delete
    suspend fun deleteWord(word: WordEntryEntity)

    @Query("SELECT word_entries.* FROM word_entries INNER JOIN entry_sources ON entry_sources.wordId = word_entries.id WHERE entry_sources.sourceId = :bookId ORDER BY entry_sources.sourceOrder, word_entries.id")
    fun observeWords(bookId: Long): Flow<List<WordEntryEntity>>

    @Query("SELECT * FROM word_entries WHERE id = :wordId")
    suspend fun getWord(wordId: Long): WordEntryEntity?

    @Query("SELECT * FROM word_entries WHERE normalizedWord = :normalizedWord AND (SELECT COUNT(*) FROM word_entries candidate WHERE candidate.normalizedWord = :normalizedWord) = 1 LIMIT 1")
    suspend fun getWordByNormalized(normalizedWord: String): WordEntryEntity?

    @Query("SELECT * FROM word_entries WHERE stableId = :stableId LIMIT 1")
    suspend fun getWordByStableId(stableId: String): WordEntryEntity?

    @Query("DELETE FROM word_entries WHERE id = :wordId")
    suspend fun deleteWordById(wordId: Long)

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM word_entries
            WHERE normalizedWord = :normalizedWord
              AND language = :language
              AND homographKey = :homographKey
              AND id != :excludedWordId
        )
        """
    )
    suspend fun wordExists(
        normalizedWord: String,
        language: String,
        homographKey: String,
        excludedWordId: Long = 0
    ): Boolean

    @Query("SELECT COALESCE(MAX(sourceOrder), -1) + 1 FROM entry_sources WHERE sourceId = :bookId")
    suspend fun nextSourceOrder(bookId: Long): Int

    @Query(
        """
        UPDATE word_entries
        SET manualFamiliarity = :familiarity,
            updatedAtEpochMillis = :timestamp
        WHERE id = :wordId
        """
    )
    suspend fun updateWordFamiliarity(
        wordId: Long,
        familiarity: String?,
        timestamp: Long
    )

    @Query(
        """
        UPDATE word_entries
        SET intakeStatus = :status,
            queuedAtEpochMillis = COALESCE(queuedAtEpochMillis, :timestamp),
            updatedAtEpochMillis = :timestamp
        WHERE id IN (:wordIds)
        """
    )
    suspend fun updateIntakeStatus(
        wordIds: List<Long>,
        status: String,
        timestamp: Long
    )

    @Query(
        """
        UPDATE word_entries
        SET intakeStatus = :status,
            proficiencyScore = MAX(proficiencyScore, :minimumScore),
            lastSeenAtEpochMillis = :timestamp,
            updatedAtEpochMillis = :timestamp
        WHERE id = :wordId
        """
    )
    suspend fun updateLearningProgress(
        wordId: Long,
        status: String,
        minimumScore: Double,
        timestamp: Long
    )

    @Query(
        """
        UPDATE word_entries
        SET intakeStatus = :status,
            proficiencyScore = :score,
            lastSeenAtEpochMillis = :timestamp,
            updatedAtEpochMillis = :timestamp
        WHERE id = :wordId
        """
    )
    suspend fun setReviewProgress(
        wordId: Long,
        status: String,
        score: Double,
        timestamp: Long
    )

    @Query(
        """
        SELECT * FROM word_entries
        WHERE normalizedWord IN (:normalizedWords)
        ORDER BY sourceOrder, id
        """
    )
    suspend fun findWords(normalizedWords: List<String>): List<WordEntryEntity>

    @Query(
        """
        SELECT
            word_entries.id AS id,
            word_entries.word AS word,
            word_entries.normalizedWord AS normalizedWord,
            CASE
                WHEN word_entries.normalizedWord IN (:normalizedWords) THEN word_entries.normalizedWord
                ELSE MIN(CASE WHEN word_forms.normalizedForm IN (:normalizedWords) THEN word_forms.normalizedForm END)
            END AS matchedText,
            word_entries.meaning AS meaning,
            word_entries.pronunciation AS pronunciation,
            MIN(review_states.status) AS learningStatus
        FROM word_entries
        LEFT JOIN word_forms ON word_forms.wordId = word_entries.id
        LEFT JOIN review_cards ON review_cards.wordId = word_entries.id
        LEFT JOIN review_states ON review_states.cardId = review_cards.id
        WHERE word_entries.normalizedWord IN (:normalizedWords)
           OR word_forms.normalizedForm IN (:normalizedWords)
        GROUP BY word_entries.id
        ORDER BY word_entries.id
        """
    )
    suspend fun findLearningMatches(
        normalizedWords: List<String>
    ): List<WordLearningMatch>

    @Query(
        """
        SELECT word_entries.* FROM word_entries
        INNER JOIN entry_sources ON entry_sources.wordId = word_entries.id
        WHERE entry_sources.sourceId = :bookId
          AND (word LIKE '%' || :query || '%' OR meaning LIKE '%' || :query || '%')
        ORDER BY entry_sources.sourceOrder, word_entries.id
        """
    )
    fun searchWords(bookId: Long, query: String): Flow<List<WordEntryEntity>>
}
