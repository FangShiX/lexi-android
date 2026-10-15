package com.finisdiscago.leximvp.core.database

import com.finisdiscago.leximvp.domain.model.DailyStatistics
import com.finisdiscago.leximvp.domain.model.CardTypePerformance
import com.finisdiscago.leximvp.domain.model.ExamPerformance
import com.finisdiscago.leximvp.domain.model.ReviewLoadDay
import com.finisdiscago.leximvp.domain.model.StudyHistoryDay
import com.finisdiscago.leximvp.domain.model.WeakSensePerformance
import com.finisdiscago.leximvp.domain.repository.StatisticsRepository
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

class RoomStatisticsRepository(
    database: LexiDatabase
) : StatisticsRepository {
    private val reviewDao = database.reviewDao()
    private val studyPlanDao = database.studyPlanDao()
    private val lexiconDao = database.lexiconDao()

    override suspend fun getDailyStatistics(
        dayStartEpochMillis: Long,
        dayEndEpochMillis: Long,
        historyDayBoundaries: List<Long>,
        futureDayBoundaries: List<Long>,
        dailyNewWordLimit: Int
    ): DailyStatistics {
        val attempts = reviewDao.getAttemptStatistics(
            dayStartEpochMillis,
            dayEndEpochMillis
        )
        val history = historyDayBoundaries.zipWithNext { start, end ->
            val row = reviewDao.getAttemptStatistics(start, end)
            StudyHistoryDay(
                dayStartEpochMillis = start,
                newWordAttempts = row.newWordAttempts,
                reviewAttempts = row.reviewAttempts,
                studyTimeMillis = row.studyTimeMillis,
                correctAnswers = row.correctAnswers,
                answeredQuestions = row.answeredQuestions
            )
        }
        val projectedNewWords = projectNewWords(
            futureDayBoundaries = futureDayBoundaries,
            dailyNewWordLimit = dailyNewWordLimit
        )
        val load = futureDayBoundaries.zipWithNext { start, end ->
            ReviewLoadDay(
                dayStartEpochMillis = start,
                count = reviewDao.countReviewLoad(start, end),
                newWordCount = projectedNewWords[start] ?: 0
            )
        }
        val lastThirtyDays = dayStartEpochMillis - 29L * 24 * 60 * 60 * 1000
        val cardTypes = reviewDao.getCardTypeStatistics(lastThirtyDays).map {
            CardTypePerformance(it.cardType, it.attempts, it.correct, it.answered)
        }
        val weakSenses = reviewDao.getWeakSenseStatistics(lastThirtyDays).map {
            WeakSensePerformance(it.word, it.senseId, it.attempts, it.mistakes)
        }
        val reviewStatus = reviewDao.getReviewStatusStatistics()
        return DailyStatistics(
            studiedWordCount = attempts.studiedWordCount,
            newWordAttempts = attempts.newWordAttempts,
            reviewAttempts = attempts.reviewAttempts,
            masteredRatings = attempts.masteredRatings,
            knowRatings = attempts.knowRatings,
            fuzzyRatings = attempts.fuzzyRatings,
            forgotRatings = attempts.forgotRatings,
            correctAnswers = attempts.correctAnswers,
            answeredQuestions = attempts.answeredQuestions,
            studyTimeMillis = attempts.studyTimeMillis,
            history = history,
            futureReviewLoad = load,
            cardTypePerformance = cardTypes,
            weakSenses = weakSenses,
            examPerformance = examPerformance(),
            matureCards = reviewStatus.matureCards,
            relearningCards = reviewStatus.relearningCards
        )
    }

    private suspend fun examPerformance(): ExamPerformance {
        var submitted = 0; var correct = 0; var answered = 0; var latest: Int? = null
        lexiconDao.allRecords().asSequence().filter { it.kind == "examAttempt" }.forEach { record ->
            val saved = runCatching { JSONObject(record.payload) }.getOrNull() ?: return@forEach
            if (!saved.optBoolean("submitted")) return@forEach
            val paper = saved.optJSONObject("paper") ?: return@forEach
            val answers = saved.optJSONObject("answers") ?: JSONObject()
            val questions = paper.optJSONArray("questions") ?: return@forEach
            var attemptCorrect = 0; var attemptAnswered = 0
            for (index in 0 until questions.length()) {
                val question = questions.getJSONObject(index); val result = gradeExamQuestion(question, answers.optString(question.optString("id")))
                if (result != null) { attemptAnswered++; if (result) attemptCorrect++ }
            }
            submitted++; correct += attemptCorrect; answered += attemptAnswered
            if (attemptAnswered > 0) latest = (attemptCorrect * 100f / attemptAnswered).toInt()
        }
        return ExamPerformance(submitted, correct, answered, latest)
    }

    private fun gradeExamQuestion(question: JSONObject, answer: String): Boolean? {
        if (question.optString("type") == "matching_multi") {
            val items = question.optJSONArray("matchingItems") ?: return null
            if ((0 until items.length()).any { items.getJSONObject(it).optJSONArray("answers")?.length() == 0 }) return null
            val actual = runCatching { JSONObject(answer) }.getOrNull() ?: return false
            return (0 until items.length()).all { index ->
                val item = items.getJSONObject(index); val expected = item.getJSONArray("answers").toStringSet()
                actual.optJSONArray(item.getString("id"))?.toStringSet() == expected
            }
        }
        if (question.optString("type") !in setOf("single", "cloze", "matching")) return null
        val expected = question.optString("answer").takeIf(String::isNotBlank) ?: return null
        return expected.trim() == answer.trim()
    }

    private suspend fun projectNewWords(
        futureDayBoundaries: List<Long>,
        dailyNewWordLimit: Int
    ): Map<Long, Int> {
        val books = studyPlanDao.getSelectionCapacity()
        val remainingByBook = books
            .associate { it.id to it.pendingCount }
            .toMutableMap()
        return futureDayBoundaries.dropLast(1).associateWith { dayStart ->
            val localDate = Instant.ofEpochMilli(dayStart)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .toString()
            val existing = studyPlanDao.countAssignments(localDate)
            var available = (dailyNewWordLimit - existing).coerceAtLeast(0)
            var projected = existing
            books.forEach { book ->
                if (available <= 0) return@forEach
                val remaining = remainingByBook[book.id] ?: 0
                val selected = minOf(book.dailySelectionLimit, remaining, available)
                remainingByBook[book.id] = remaining - selected
                projected += selected
                available -= selected
            }
            projected
        }
    }
}

private fun org.json.JSONArray.toStringSet(): Set<String> = (0 until length()).map { optString(it) }.toSet()
