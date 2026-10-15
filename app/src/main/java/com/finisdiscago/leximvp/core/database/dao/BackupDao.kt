package com.finisdiscago.leximvp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.finisdiscago.leximvp.core.database.entity.ArticleEntity
import com.finisdiscago.leximvp.core.database.entity.ArticleTokenEntity
import com.finisdiscago.leximvp.core.database.entity.DailyStudyAssignmentEntity
import com.finisdiscago.leximvp.core.database.entity.LearningAttemptEntity
import com.finisdiscago.leximvp.core.database.entity.ReviewCardEntity
import com.finisdiscago.leximvp.core.database.entity.ReviewStateEntity
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

@Dao
interface BackupDao {
    @Query("SELECT COUNT(*) FROM vocabulary_books")
    suspend fun countBooks(): Int

    @Query("SELECT COUNT(*) FROM word_entries")
    suspend fun countWords(): Int

    @Query("SELECT COUNT(*) FROM word_forms")
    suspend fun countForms(): Int

    @Query("SELECT COUNT(*) FROM source_records")
    suspend fun countSources(): Int

    @Query("SELECT COUNT(*) FROM entry_sources")
    suspend fun countEntrySources(): Int

    @Query("SELECT COUNT(*) FROM senses") suspend fun countSenses(): Int
    @Query("SELECT COUNT(*) FROM examples") suspend fun countExamples(): Int
    @Query("SELECT COUNT(*) FROM entry_relations") suspend fun countRelations(): Int
    @Query("SELECT COUNT(*) FROM entry_tags") suspend fun countEntryTags(): Int
    @Query("SELECT COUNT(*) FROM frequency_records") suspend fun countFrequencyRecords(): Int

    @Query("SELECT COUNT(*) FROM review_cards")
    suspend fun countCards(): Int

    @Query("SELECT COUNT(*) FROM review_states")
    suspend fun countStates(): Int

    @Query("SELECT COUNT(*) FROM learning_attempts")
    suspend fun countAttempts(): Int

    @Query("SELECT COUNT(*) FROM articles")
    suspend fun countArticles(): Int

    @Query("SELECT COUNT(*) FROM article_tokens")
    suspend fun countTokens(): Int

    @Query("SELECT COUNT(*) FROM daily_study_assignments")
    suspend fun countAssignments(): Int

    @Query("SELECT * FROM vocabulary_books ORDER BY id")
    suspend fun getBooks(): List<VocabularyBookEntity>

    @Query("SELECT * FROM word_entries ORDER BY id")
    suspend fun getWords(): List<WordEntryEntity>

    @Query("SELECT * FROM review_cards ORDER BY id")
    suspend fun getCards(): List<ReviewCardEntity>

    @Query("SELECT * FROM review_states ORDER BY id")
    suspend fun getStates(): List<ReviewStateEntity>

    @Query("SELECT * FROM learning_attempts ORDER BY id")
    suspend fun getAttempts(): List<LearningAttemptEntity>

    @Query("SELECT * FROM articles ORDER BY id")
    suspend fun getArticles(): List<ArticleEntity>

    @Query("SELECT * FROM article_tokens ORDER BY id")
    suspend fun getTokens(): List<ArticleTokenEntity>

    @Query("SELECT * FROM daily_study_assignments ORDER BY id")
    suspend fun getAssignments(): List<DailyStudyAssignmentEntity>

    @Query("SELECT id FROM word_entries WHERE stableId = :stableId OR (language = :language AND normalizedWord = :normalizedWord AND homographKey = :homographKey) ORDER BY CASE WHEN stableId = :stableId THEN 0 ELSE 1 END LIMIT 1")
    suspend fun findWordId(stableId: String, language: String, normalizedWord: String, homographKey: String): Long?

    @Query("SELECT id FROM review_cards WHERE stableId = :stableId LIMIT 1")
    suspend fun findCardId(stableId: String): Long?

    @Query("SELECT id FROM review_cards WHERE wordId = :wordId AND cardType = :cardType AND senseId IS NULL AND exampleId IS NULL LIMIT 1")
    suspend fun findLegacyCardId(wordId: Long, cardType: String): Long?

    @Query("SELECT EXISTS(SELECT 1 FROM review_states WHERE cardId = :cardId)")
    suspend fun hasState(cardId: Long): Boolean

    @Query("DELETE FROM daily_study_assignments")
    suspend fun deleteAssignments()

    @Query("DELETE FROM article_tokens")
    suspend fun deleteTokens()

    @Query("DELETE FROM articles")
    suspend fun deleteArticles()

    @Query("DELETE FROM learning_attempts")
    suspend fun deleteAttempts()

    @Query("DELETE FROM review_states")
    suspend fun deleteStates()

    @Query("DELETE FROM review_cards")
    suspend fun deleteCards()

    @Query("DELETE FROM word_entries")
    suspend fun deleteWords()

    @Query("DELETE FROM word_forms")
    suspend fun deleteForms()

    @Query("DELETE FROM entry_sources")
    suspend fun deleteEntrySources()

    @Query("DELETE FROM source_records")
    suspend fun deleteSources()

    @Query("DELETE FROM entry_relations") suspend fun deleteRelations()
    @Query("DELETE FROM entry_tags") suspend fun deleteEntryTags()
    @Query("DELETE FROM frequency_records") suspend fun deleteFrequencyRecords()
    @Query("DELETE FROM examples") suspend fun deleteExamples()
    @Query("DELETE FROM senses") suspend fun deleteSenses()

    @Query("DELETE FROM vocabulary_books")
    suspend fun deleteBooks()

    @Insert
    suspend fun insertBook(book: VocabularyBookEntity): Long

    @Insert
    suspend fun insertWord(word: WordEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertForm(form: WordFormEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: SourceRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntrySource(source: EntrySourceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSense(sense: SenseEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertExample(example: ExampleEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRelation(relation: EntryRelationEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertEntryTag(tag: EntryTagEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertFrequencyRecord(record: FrequencyRecordEntity): Long

    @Insert
    suspend fun insertCard(card: ReviewCardEntity): Long

    @Insert
    suspend fun insertState(state: ReviewStateEntity): Long

    @Insert
    suspend fun insertAttempt(attempt: LearningAttemptEntity): Long

    @Insert
    suspend fun insertArticle(article: ArticleEntity): Long

    @Insert
    suspend fun insertToken(token: ArticleTokenEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAssignment(assignment: DailyStudyAssignmentEntity): Long
}
