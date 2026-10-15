package com.finisdiscago.leximvp.domain.model

data class DailyStatistics(
    val studiedWordCount: Int,
    val newWordAttempts: Int,
    val reviewAttempts: Int,
    val masteredRatings: Int,
    val knowRatings: Int,
    val fuzzyRatings: Int,
    val forgotRatings: Int,
    val correctAnswers: Int,
    val answeredQuestions: Int,
    val studyTimeMillis: Long,
    val history: List<StudyHistoryDay>,
    val futureReviewLoad: List<ReviewLoadDay>,
    val cardTypePerformance: List<CardTypePerformance> = emptyList(),
    val weakSenses: List<WeakSensePerformance> = emptyList(),
    val examPerformance: ExamPerformance = ExamPerformance(),
    val matureCards: Int = 0,
    val relearningCards: Int = 0
) {
    val accuracy: Float
        get() = if (answeredQuestions == 0) {
            0f
        } else {
            correctAnswers.toFloat() / answeredQuestions
        }
}

data class StudyHistoryDay(
    val dayStartEpochMillis: Long,
    val newWordAttempts: Int,
    val reviewAttempts: Int,
    val studyTimeMillis: Long,
    val correctAnswers: Int = 0,
    val answeredQuestions: Int = 0
) {
    val totalAttempts: Int get() = newWordAttempts + reviewAttempts
    val accuracy: Float? get() = answeredQuestions.takeIf { it > 0 }?.let { correctAnswers.toFloat() / it }
}

data class ReviewLoadDay(
    val dayStartEpochMillis: Long,
    val count: Int,
    val newWordCount: Int = 0
) {
    val totalCount: Int get() = count + newWordCount
}

data class CardTypePerformance(val cardType: String, val attempts: Int, val correct: Int, val answered: Int) {
    val accuracy: Float? get() = answered.takeIf { it > 0 }?.let { correct.toFloat() / it }
}

data class WeakSensePerformance(val word: String, val senseId: String, val attempts: Int, val mistakes: Int)

data class ExamPerformance(
    val submittedAttempts: Int = 0,
    val objectiveCorrect: Int = 0,
    val objectiveAnswered: Int = 0,
    val latestScore: Int? = null
) {
    val accuracy: Float? get() = objectiveAnswered.takeIf { it > 0 }?.let { objectiveCorrect.toFloat() / it }
}
