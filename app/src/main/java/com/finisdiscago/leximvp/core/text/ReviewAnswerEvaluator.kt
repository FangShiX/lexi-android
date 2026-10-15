package com.finisdiscago.leximvp.core.text

import com.finisdiscago.leximvp.domain.model.CardType
import com.finisdiscago.leximvp.domain.model.ReviewAnswerResult

/** Adapts the recovered spelling preparation to Lexi's existing exercise types. */
object ReviewAnswerEvaluator {
    fun evaluate(type: CardType, answer: String, expected: String): ReviewAnswerResult = when (type) {
        CardType.AudioToSpelling, CardType.AudioToChoice,
        CardType.SentenceCloze, CardType.SynonymDiscrimination -> {
            val normalizedAnswer = SpellingTextNormalizer.normalize(answer.trim())
            val normalizedExpected = SpellingTextNormalizer.normalize(expected.trim())
            // A non-Latin input must not become a correct empty answer after preparation.
            ReviewAnswerResult(
                isCorrect = normalizedAnswer.isNotEmpty() && normalizedExpected.isNotEmpty() &&
                    normalizedAnswer == normalizedExpected,
                normalizedAnswer = normalizedAnswer
            )
        }
        else -> ReviewAnswerResult(isCorrect = null, normalizedAnswer = null)
    }
}
