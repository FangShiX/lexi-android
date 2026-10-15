package com.finisdiscago.leximvp.core.database

import androidx.room.withTransaction
import com.finisdiscago.leximvp.core.database.entity.LearningAttemptEntity
import com.finisdiscago.leximvp.core.scheduler.IntraDayLearningScheduler
import com.finisdiscago.leximvp.core.text.RichWordContentParser
import com.finisdiscago.leximvp.domain.model.LearningQueueItem
import com.finisdiscago.leximvp.domain.model.LearningRating
import com.finisdiscago.leximvp.domain.repository.LearningRepository
import java.time.Instant
import java.time.ZoneId

class RoomLearningRepository(
    private val database: LexiDatabase,
    private val dailyStudyPlanner: DailyStudyPlanner = DailyStudyPlanner(database)
) : LearningRepository {
    private var selection = ""
    override fun selectWords(ids: List<Long>) { selection = if (ids.isEmpty()) "" else ids.joinToString(",", ",", ",") }

    private val reviewDao = database.reviewDao()
    private val vocabularyDao = database.vocabularyDao()

    override suspend fun getNextDueWord(
        nowEpochMillis: Long,
        dayStartEpochMillis: Long,
        dailyNewWordLimit: Int
    ): LearningQueueItem? {
        dailyStudyPlanner.ensurePlan(localDate(dayStartEpochMillis), dailyNewWordLimit)
        return reviewDao.getNextDueLearningWord(
            nowEpochMillis,
            dayStartEpochMillis,
            if (selection.isEmpty()) dailyNewWordLimit else Int.MAX_VALUE,
            selection
        )?.let {
            val richContent = RichWordContentParser.parseOrNull(it.richContentJson)
            val matchedSense = richContent?.selectSense(it.attemptsToday.toLong())
            LearningQueueItem(
                wordId = it.wordId,
                cardId = it.cardId,
                word = it.word,
                meaning = matchedSense?.let { sense -> sense.meaningZh.ifBlank { sense.definitionEn } } ?: it.meaning,
                pronunciation = it.pronunciation,
                exampleSentence = matchedSense?.examples?.firstOrNull()?.en ?: it.exampleSentence,
                exampleTranslation = matchedSense?.examples?.firstOrNull()?.zh
                    ?: it.exampleTranslation,
                richContent = richContent,
                matchedSense = matchedSense,
                dueAtEpochMillis = it.dueAtEpochMillis,
                attemptsToday = it.attemptsToday
            )
        }
    }

    override suspend fun countDueWords(
        nowEpochMillis: Long,
        dayStartEpochMillis: Long,
        dailyNewWordLimit: Int
    ): Int {
        dailyStudyPlanner.ensurePlan(localDate(dayStartEpochMillis), dailyNewWordLimit)
        return reviewDao.countDueLearningWords(
            nowEpochMillis,
            dayStartEpochMillis,
            if (selection.isEmpty()) dailyNewWordLimit else Int.MAX_VALUE,
            selection
        )
    }

    override suspend fun countAllDueWords(
        nowEpochMillis: Long,
        dayStartEpochMillis: Long,
        dailyNewWordLimit: Int
    ): Int {
        dailyStudyPlanner.ensurePlan(localDate(dayStartEpochMillis), dailyNewWordLimit)
        return reviewDao.countDueLearningWords(nowEpochMillis, dayStartEpochMillis, dailyNewWordLimit, "")
    }

    override suspend fun recordRating(
        item: LearningQueueItem,
        rating: LearningRating,
        responseTimeMillis: Long,
        reviewedAtEpochMillis: Long,
        dayStartEpochMillis: Long,
        sessionId: String
    ) {
        database.withTransaction {
            reviewDao.insertAttempt(
                LearningAttemptEntity(
                    sessionId = sessionId,
                    wordId = item.wordId,
                    cardId = item.cardId,
                    rating = rating.score,
                    responseTimeMillis = responseTimeMillis,
                    attemptKind = "NewWord",
                    attemptedAtEpochMillis = reviewedAtEpochMillis
                )
            )
            val attempts = reviewDao.getWordAttempts(
                wordId = item.wordId,
                startEpochMillis = reviewDao.getStateForCard(item.cardId)?.let { state ->
                    if (state.status == "Learning" || state.status == "New") 0L else dayStartEpochMillis
                } ?: dayStartEpochMillis,
                endEpochMillis = reviewedAtEpochMillis + 1
            ).filter { it.attemptKind == "NewWord" }
            val ratings = attempts.map { attempt ->
                LearningRating.entries.first { it.score == attempt.rating }
            }
            val schedule = IntraDayLearningScheduler.schedule(
                ratings = ratings,
                reviewedAtEpochMillis = reviewedAtEpochMillis
            )

            if (schedule.graduated) {
                vocabularyDao.updateLearningProgress(
                    wordId = item.wordId,
                    status = "Reviewing",
                    minimumScore = 0.6,
                    timestamp = reviewedAtEpochMillis
                )
                reviewDao.getStatesForWord(item.wordId).forEach { state ->
                    reviewDao.updateState(
                        state.copy(
                            status = "Review",
                            dueAtEpochMillis = schedule.nextDueAtEpochMillis,
                            intervalDays = schedule.initialIntervalDays,
                            repetitionCount = 1,
                            lastReviewedAtEpochMillis = reviewedAtEpochMillis
                        )
                    )
                }
            } else {
                vocabularyDao.updateLearningProgress(
                    wordId = item.wordId,
                    status = "Learning",
                    minimumScore = 0.25,
                    timestamp = reviewedAtEpochMillis
                )
                val state = reviewDao.getStateForCard(item.cardId) ?: return@withTransaction
                reviewDao.updateState(
                    state.copy(
                        status = "Learning",
                        dueAtEpochMillis = schedule.nextDueAtEpochMillis,
                        lastReviewedAtEpochMillis = reviewedAtEpochMillis
                    )
                )
            }
        }
    }

    private fun localDate(dayStartEpochMillis: Long): String =
        Instant.ofEpochMilli(dayStartEpochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString()
}
