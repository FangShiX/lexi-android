package com.finisdiscago.leximvp.core.database

import androidx.room.withTransaction
import com.finisdiscago.leximvp.core.database.entity.LearningAttemptEntity
import com.finisdiscago.leximvp.core.scheduler.ReviewRating
import com.finisdiscago.leximvp.core.scheduler.SrsScheduler
import com.finisdiscago.leximvp.core.text.ReviewAnswerEvaluator
import com.finisdiscago.leximvp.core.text.RichWordContentParser
import com.finisdiscago.leximvp.core.text.ExerciseDefinition
import com.finisdiscago.leximvp.domain.model.CardType
import com.finisdiscago.leximvp.domain.model.ReviewAnswerResult
import com.finisdiscago.leximvp.domain.model.ReviewQueueItem
import com.finisdiscago.leximvp.domain.model.ReviewState
import com.finisdiscago.leximvp.domain.model.ReviewStatus
import com.finisdiscago.leximvp.domain.repository.ReviewRepository
import java.time.Instant

class RoomReviewRepository(
    private val database: LexiDatabase
) : ReviewRepository {
    private val reviewDao = database.reviewDao()
    private val vocabularyDao = database.vocabularyDao()

    override suspend fun getNextDueCard(
        nowEpochMillis: Long,
        dayStartEpochMillis: Long,
        dailyReviewLimit: Int,
        excludedWordId: Long?
    ): ReviewQueueItem? {
        if (reviewDao.countReviewsCompletedToday(dayStartEpochMillis) >= dailyReviewLimit) {
            return null
        }
        val preferred = reviewDao.getDueCards(nowEpochMillis, excludedWordId, 1).firstOrNull()
        val due = preferred ?: reviewDao.getDueCards(nowEpochMillis, null, 1).firstOrNull()
        return due?.let {
            val richContent = RichWordContentParser.parseOrNull(it.richContentJson)
            val attemptIndex = reviewDao.countAttemptsForCard(it.cardId)
            val matchedSense = if (it.senseId == null) richContent?.selectSense(attemptIndex.toLong()) else null
            val exercises = com.finisdiscago.leximvp.core.text.ExerciseCapabilityResolver.exercises(it.word, it.richContentJson).filter { exercise -> exercise.type.name == it.cardType }
            val exercise = when {
                it.cardType == CardType.SentenceCloze.name && !it.structuredExample.isNullOrBlank() -> {
                    val target = Regex("(?i)(?<![\\p{L}])${Regex.escape(it.word)}(?![\\p{L}])")
                    ExerciseDefinition(
                        id = it.exampleId ?: "card:${it.cardId}", type = CardType.SentenceCloze,
                        prompt = target.replace(it.structuredExample, "____"), answer = it.word,
                        explanation = it.structuredExampleTranslation.orEmpty()
                    )
                }
                it.cardStableId.contains(":exercise:") -> exercises.firstOrNull { exercise ->
                    it.cardStableId.endsWith(":exercise:${exercise.id}")
                }
                else -> exercises.takeIf { list -> list.isNotEmpty() }?.let { list -> list[attemptIndex % list.size] }
            }
            ReviewQueueItem(
                cardId = it.cardId,
                exercise = exercise,
                wordId = it.wordId,
                cardType = CardType.valueOf(it.cardType),
                word = it.word,
                meaning = it.senseMeaningZh?.ifBlank { it.senseDefinitionEn.orEmpty() }?.ifBlank { null }
                    ?: matchedSense?.meaningZh ?: it.meaning,
                pronunciation = it.pronunciation,
                exampleSentence = it.structuredExample ?: matchedSense?.examples?.firstOrNull()?.en ?: it.exampleSentence,
                exampleTranslation = it.structuredExampleTranslation ?: matchedSense?.examples?.firstOrNull()?.zh
                    ?: it.exampleTranslation,
                richContent = richContent,
                matchedSense = matchedSense,
                dueAtEpochMillis = it.dueAtEpochMillis,
                intervalDays = it.intervalDays,
                easeFactor = it.easeFactor
            )
        }
    }

    override suspend fun countDueCards(
        nowEpochMillis: Long,
        dayStartEpochMillis: Long,
        dailyReviewLimit: Int
    ): Int {
        val remaining = dailyReviewLimit -
            reviewDao.countReviewsCompletedToday(dayStartEpochMillis)
        return reviewDao.countDueReviewCards(nowEpochMillis).coerceAtMost(remaining.coerceAtLeast(0))
    }

    override fun checkAnswer(
        item: ReviewQueueItem,
        answer: String
    ): ReviewAnswerResult {
        return ReviewAnswerEvaluator.evaluate(item.cardType, answer, item.exercise?.answer ?: item.word)
    }

    override suspend fun recordReview(
        item: ReviewQueueItem,
        rating: ReviewRating,
        answerResult: ReviewAnswerResult,
        responseTimeMillis: Long,
        reviewedAtEpochMillis: Long,
        sessionId: String
    ) {
        database.withTransaction {
            val entity = reviewDao.getStateForCard(item.cardId) ?: return@withTransaction
            val scheduled = SrsScheduler.schedule(
                state = ReviewState(
                    id = entity.id,
                    wordId = item.wordId,
                    cardId = item.cardId,
                    status = ReviewStatus.valueOf(entity.status),
                    dueAt = Instant.ofEpochMilli(entity.dueAtEpochMillis),
                    intervalDays = entity.intervalDays,
                    easeFactor = entity.easeFactor,
                    repetitionCount = entity.repetitionCount,
                    lapseCount = entity.lapseCount
                ),
                rating = if (answerResult.isCorrect == false) ReviewRating.Again else rating,
                reviewedAt = Instant.ofEpochMilli(reviewedAtEpochMillis)
            )
            reviewDao.insertAttempt(
                LearningAttemptEntity(
                    sessionId = sessionId,
                    wordId = item.wordId,
                    cardId = item.cardId,
                    rating = rating.ordinal,
                    isCorrect = answerResult.isCorrect,
                    responseTimeMillis = responseTimeMillis,
                    attemptKind = "Review",
                    attemptedAtEpochMillis = reviewedAtEpochMillis
                )
            )
            reviewDao.updateState(
                entity.copy(
                    status = scheduled.status.name,
                    dueAtEpochMillis = scheduled.dueAt.toEpochMilli(),
                    intervalDays = scheduled.intervalDays,
                    easeFactor = scheduled.easeFactor,
                    repetitionCount = scheduled.repetitionCount,
                    lapseCount = scheduled.lapseCount,
                    lastReviewedAtEpochMillis = reviewedAtEpochMillis
                )
            )
            WordAssessmentService(database).recompute(item.wordId, reviewedAtEpochMillis,
                clearManual = answerResult.isCorrect == false || rating == ReviewRating.Again)

        }
    }
}
