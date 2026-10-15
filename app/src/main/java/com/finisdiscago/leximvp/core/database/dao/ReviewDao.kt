package com.finisdiscago.leximvp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.finisdiscago.leximvp.core.database.entity.LearningAttemptEntity
import com.finisdiscago.leximvp.core.database.entity.ReviewCardEntity
import com.finisdiscago.leximvp.core.database.entity.ReviewStateEntity
import kotlinx.coroutines.flow.Flow

data class DueReviewCard(
    val cardId: Long,
    val cardStableId: String,
    val wordId: Long,
    val cardType: String,
    val senseId: String?,
    val exampleId: String?,
    val word: String,
    val meaning: String,
    val pronunciation: String?,
    val exampleSentence: String?,
    val exampleTranslation: String?,
    val richContentJson: String?,
    val senseMeaningZh: String?,
    val senseDefinitionEn: String?,
    val structuredExample: String?,
    val structuredExampleTranslation: String?,
    val status: String,
    val dueAtEpochMillis: Long,
    val intervalDays: Double,
    val easeFactor: Double
)

data class DueLearningWord(
    val wordId: Long,
    val cardId: Long,
    val word: String,
    val meaning: String,
    val pronunciation: String?,
    val exampleSentence: String?,
    val exampleTranslation: String?,
    val richContentJson: String?,
    val dueAtEpochMillis: Long,
    val attemptsToday: Int
)

data class AttemptStatisticsRow(
    val studiedWordCount: Int,
    val newWordAttempts: Int,
    val reviewAttempts: Int,
    val masteredRatings: Int,
    val knowRatings: Int,
    val fuzzyRatings: Int,
    val forgotRatings: Int,
    val correctAnswers: Int,
    val answeredQuestions: Int,
    val studyTimeMillis: Long
)

data class CardTypeStatisticsRow(val cardType: String, val attempts: Int, val correct: Int, val answered: Int)
data class WeakSenseStatisticsRow(val word: String, val senseId: String, val attempts: Int, val mistakes: Int)
data class ReviewStatusStatisticsRow(val matureCards: Int, val relearningCards: Int)

@Dao
interface ReviewDao {
    @Query("SELECT COUNT(*) FROM learning_attempts WHERE cardId = :cardId")
    suspend fun countAttemptsForCard(cardId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCards(cards: List<ReviewCardEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStates(states: List<ReviewStateEntity>)

    @Update
    suspend fun updateState(state: ReviewStateEntity)

    @Insert
    suspend fun insertAttempt(attempt: LearningAttemptEntity): Long

    @Query("SELECT * FROM review_cards WHERE wordId = :wordId ORDER BY cardType")
    suspend fun getCardsForWord(wordId: Long): List<ReviewCardEntity>

    @Query("UPDATE review_cards SET enabled = :enabled WHERE senseId = :senseId")
    suspend fun setCardsEnabledForSense(senseId: String, enabled: Boolean)

    @Query(
        """
        SELECT review_states.* FROM review_states
        INNER JOIN review_cards ON review_cards.id = review_states.cardId
        WHERE review_cards.wordId = :wordId
        ORDER BY review_cards.cardType
        """
    )
    suspend fun getStatesForWord(wordId: Long): List<ReviewStateEntity>

    @Query("SELECT * FROM review_states WHERE cardId = :cardId")
    suspend fun getStateForCard(cardId: Long): ReviewStateEntity?

    @Query(
        """
        SELECT
            word_entries.id AS wordId,
            review_cards.id AS cardId,
            word_entries.word AS word,
            word_entries.meaning AS meaning,
            word_entries.pronunciation AS pronunciation,
            word_entries.exampleSentence AS exampleSentence,
            word_entries.exampleTranslation AS exampleTranslation,
            word_entries.richContentJson AS richContentJson,
            review_states.dueAtEpochMillis AS dueAtEpochMillis,
            (
                SELECT COUNT(*) FROM learning_attempts
                WHERE learning_attempts.wordId = word_entries.id
                  AND learning_attempts.attemptKind = 'NewWord'
                  AND learning_attempts.attemptedAtEpochMillis >= :dayStartEpochMillis
            ) AS attemptsToday
        FROM review_cards
        INNER JOIN review_states ON review_states.cardId = review_cards.id
        INNER JOIN word_entries ON word_entries.id = review_cards.wordId
        WHERE review_cards.enabled = 1
          AND review_cards.cardType = 'ForeignToMeaning'
          AND review_cards.senseId IS NULL
          AND (:selection = '' OR instr(:selection, ',' || review_cards.wordId || ',') > 0)
          AND word_entries.intakeStatus != 'Pending'
          AND review_states.status IN ('New', 'Learning')
          AND review_states.dueAtEpochMillis <= :nowEpochMillis
          AND (
              (
                  SELECT COUNT(*) FROM learning_attempts existing_attempts
                  WHERE existing_attempts.wordId = word_entries.id
                    AND existing_attempts.attemptKind = 'NewWord'
                    AND existing_attempts.attemptedAtEpochMillis >= :dayStartEpochMillis
              ) > 0
              OR (
                  SELECT COUNT(DISTINCT started_words.wordId)
                  FROM learning_attempts started_words
                  WHERE started_words.attemptKind = 'NewWord'
                    AND started_words.attemptedAtEpochMillis >= :dayStartEpochMillis
              ) < :dailyNewWordLimit
          )
        ORDER BY review_states.dueAtEpochMillis ASC, word_entries.id ASC
        LIMIT 1
        """
    )
    suspend fun getNextDueLearningWord(
        nowEpochMillis: Long,
        dayStartEpochMillis: Long,
        dailyNewWordLimit: Int,
        selection: String = ""
    ): DueLearningWord?

    @Query(
        """
        SELECT COUNT(*) FROM review_cards
        INNER JOIN review_states ON review_states.cardId = review_cards.id
        WHERE review_cards.enabled = 1
          AND review_cards.cardType = 'ForeignToMeaning'
          AND review_cards.senseId IS NULL
          AND (:selection = '' OR instr(:selection, ',' || review_cards.wordId || ',') > 0)
          AND EXISTS (
              SELECT 1 FROM word_entries
              WHERE word_entries.id = review_cards.wordId
                AND word_entries.intakeStatus != 'Pending'
          )
          AND review_states.status IN ('New', 'Learning')
          AND review_states.dueAtEpochMillis <= :nowEpochMillis
          AND (
              (
                  SELECT COUNT(*) FROM learning_attempts existing_attempts
                  WHERE existing_attempts.wordId = review_cards.wordId
                    AND existing_attempts.attemptKind = 'NewWord'
                    AND existing_attempts.attemptedAtEpochMillis >= :dayStartEpochMillis
              ) > 0
              OR (
                  SELECT COUNT(DISTINCT started_words.wordId)
                  FROM learning_attempts started_words
                  WHERE started_words.attemptKind = 'NewWord'
                    AND started_words.attemptedAtEpochMillis >= :dayStartEpochMillis
              ) < :dailyNewWordLimit
          )
        """
    )
    suspend fun countDueLearningWords(
        nowEpochMillis: Long,
        dayStartEpochMillis: Long,
        dailyNewWordLimit: Int,
        selection: String = ""
    ): Int

    @Query(
        """
        SELECT
            review_cards.id AS cardId,
            review_cards.stableId AS cardStableId,
            review_cards.wordId AS wordId,
            review_cards.cardType AS cardType,
            review_cards.senseId AS senseId,
            review_cards.exampleId AS exampleId,
            word_entries.word AS word,
            word_entries.meaning AS meaning,
            word_entries.pronunciation AS pronunciation,
            word_entries.exampleSentence AS exampleSentence,
            word_entries.exampleTranslation AS exampleTranslation,
            word_entries.richContentJson AS richContentJson,
            senses.meaningZh AS senseMeaningZh,
            senses.definitionEn AS senseDefinitionEn,
            examples.text AS structuredExample,
            examples.translation AS structuredExampleTranslation,
            review_states.status AS status,
            review_states.dueAtEpochMillis AS dueAtEpochMillis,
            review_states.intervalDays AS intervalDays,
            review_states.easeFactor AS easeFactor
        FROM review_cards
        INNER JOIN review_states ON review_states.cardId = review_cards.id
        INNER JOIN word_entries ON word_entries.id = review_cards.wordId
        LEFT JOIN senses ON senses.stableId = review_cards.senseId AND senses.active = 1
        LEFT JOIN examples ON examples.stableId = review_cards.exampleId AND examples.active = 1
        WHERE review_cards.enabled = 1
          AND review_states.dueAtEpochMillis <= :nowEpochMillis
          AND review_states.status IN ('Review', 'Relearning', 'Mastered')
          AND (:excludedWordId IS NULL OR review_cards.wordId != :excludedWordId)
        ORDER BY review_states.dueAtEpochMillis ASC
        LIMIT :limit
        """
    )
    suspend fun getDueCards(
        nowEpochMillis: Long,
        excludedWordId: Long?,
        limit: Int
    ): List<DueReviewCard>

    @Query(
        """
        SELECT * FROM learning_attempts
        WHERE wordId = :wordId
          AND attemptedAtEpochMillis >= :startEpochMillis
          AND attemptedAtEpochMillis < :endEpochMillis
        ORDER BY attemptedAtEpochMillis
        """
    )
    suspend fun getWordAttempts(
        wordId: Long,
        startEpochMillis: Long,
        endEpochMillis: Long
    ): List<LearningAttemptEntity>

    @Query(
        """
        SELECT COUNT(*) FROM review_states
        INNER JOIN review_cards ON review_cards.id = review_states.cardId
        WHERE dueAtEpochMillis <= :nowEpochMillis
          AND review_cards.enabled = 1
          AND status IN ('Review', 'Relearning', 'Mastered')
        """
    )
    fun observeDueCount(nowEpochMillis: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM review_states
        INNER JOIN review_cards ON review_cards.id = review_states.cardId
        WHERE dueAtEpochMillis <= :nowEpochMillis
          AND review_cards.enabled = 1
          AND status IN ('Review', 'Relearning', 'Mastered')
        """
    )
    suspend fun countDueReviewCards(nowEpochMillis: Long): Int

    @Query("SELECT COUNT(DISTINCT review_cards.wordId) FROM review_states INNER JOIN review_cards ON review_cards.id = review_states.cardId WHERE review_cards.enabled = 1 AND review_cards.senseId IS NULL AND review_cards.cardType = 'ForeignToMeaning' AND review_states.status = 'Learning'")
    suspend fun countIntraDayLearningWords(): Int

    @Query(
        """
        SELECT COUNT(*) FROM learning_attempts
        WHERE attemptKind = 'Review'
          AND attemptedAtEpochMillis >= :dayStartEpochMillis
        """
    )
    suspend fun countReviewsCompletedToday(dayStartEpochMillis: Long): Int

    @Query(
        """
        SELECT
            COUNT(DISTINCT wordId) AS studiedWordCount,
            COALESCE(SUM(CASE WHEN attemptKind = 'NewWord' THEN 1 ELSE 0 END), 0) AS newWordAttempts,
            COALESCE(SUM(CASE WHEN attemptKind = 'Review' THEN 1 ELSE 0 END), 0) AS reviewAttempts,
            COALESCE(SUM(CASE WHEN attemptKind = 'NewWord' AND rating = 3 THEN 1 ELSE 0 END), 0) AS masteredRatings,
            COALESCE(SUM(CASE WHEN attemptKind = 'NewWord' AND rating = 2 THEN 1 ELSE 0 END), 0) AS knowRatings,
            COALESCE(SUM(CASE WHEN attemptKind = 'NewWord' AND rating = 1 THEN 1 ELSE 0 END), 0) AS fuzzyRatings,
            COALESCE(SUM(CASE WHEN attemptKind = 'NewWord' AND rating = 0 THEN 1 ELSE 0 END), 0) AS forgotRatings,
            COALESCE(SUM(CASE WHEN isCorrect = 1 THEN 1 ELSE 0 END), 0) AS correctAnswers,
            COALESCE(SUM(CASE WHEN isCorrect IS NOT NULL THEN 1 ELSE 0 END), 0) AS answeredQuestions,
            COALESCE(SUM(responseTimeMillis), 0) AS studyTimeMillis
        FROM learning_attempts
        WHERE attemptedAtEpochMillis >= :startEpochMillis
          AND attemptedAtEpochMillis < :endEpochMillis
        """
    )
    suspend fun getAttemptStatistics(
        startEpochMillis: Long,
        endEpochMillis: Long
    ): AttemptStatisticsRow

    @Query(
        """SELECT review_cards.cardType AS cardType, COUNT(*) AS attempts,
            COALESCE(SUM(CASE WHEN learning_attempts.isCorrect = 1 THEN 1 ELSE 0 END), 0) AS correct,
            COALESCE(SUM(CASE WHEN learning_attempts.isCorrect IS NOT NULL THEN 1 ELSE 0 END), 0) AS answered
        FROM learning_attempts INNER JOIN review_cards ON review_cards.id = learning_attempts.cardId
        WHERE attemptedAtEpochMillis >= :startEpochMillis
        GROUP BY review_cards.cardType ORDER BY attempts DESC"""
    )
    suspend fun getCardTypeStatistics(startEpochMillis: Long): List<CardTypeStatisticsRow>

    @Query(
        """SELECT word_entries.word AS word, review_cards.senseId AS senseId, COUNT(*) AS attempts,
            COALESCE(SUM(CASE WHEN learning_attempts.isCorrect = 0 OR learning_attempts.rating <= 1 THEN 1 ELSE 0 END), 0) AS mistakes
        FROM learning_attempts
        INNER JOIN review_cards ON review_cards.id = learning_attempts.cardId
        INNER JOIN word_entries ON word_entries.id = learning_attempts.wordId
        WHERE review_cards.senseId IS NOT NULL AND attemptedAtEpochMillis >= :startEpochMillis
        GROUP BY review_cards.senseId HAVING mistakes > 0
        ORDER BY mistakes DESC, attempts DESC LIMIT :limit"""
    )
    suspend fun getWeakSenseStatistics(startEpochMillis: Long, limit: Int = 8): List<WeakSenseStatisticsRow>

    @Query(
        """SELECT COALESCE(SUM(CASE WHEN status = 'Mastered' THEN 1 ELSE 0 END), 0) AS matureCards,
            COALESCE(SUM(CASE WHEN status = 'Relearning' THEN 1 ELSE 0 END), 0) AS relearningCards
        FROM review_states INNER JOIN review_cards ON review_cards.id = review_states.cardId
        WHERE review_cards.enabled = 1"""
    )
    suspend fun getReviewStatusStatistics(): ReviewStatusStatisticsRow

    @Query(
        """
        SELECT COUNT(*) FROM review_states
        WHERE dueAtEpochMillis >= :startEpochMillis
          AND dueAtEpochMillis < :endEpochMillis
          AND status IN ('Review', 'Relearning', 'Mastered')
        """
    )
    suspend fun countReviewLoad(
        startEpochMillis: Long,
        endEpochMillis: Long
    ): Int

}
