package com.finisdiscago.leximvp.core.text

import com.finisdiscago.leximvp.domain.model.CardType
import org.junit.Assert.*
import org.junit.Test

class ReviewAnswerEvaluatorTest {
    @Test fun spellingDoesNotAcceptADifferentInflection() {
        listOf("walked" to "walk", "cats" to "cat", "went" to "go").forEach { (answer, expected) ->
            assertEquals(false, ReviewAnswerEvaluator.evaluate(CardType.AudioToSpelling, answer, expected).isCorrect)
        }
    }

    @Test fun preparedSpellingAcceptsCaseAndDecomposedAccents() {
        assertEquals(true, ReviewAnswerEvaluator.evaluate(CardType.AudioToSpelling, "  CAFÉ  ", "cafe").isCorrect)
        assertEquals(true, ReviewAnswerEvaluator.evaluate(CardType.AudioToSpelling, "cafe\u0301", "café").isCorrect)
    }

    @Test fun phrasesKeepTheirSpacesAndPunctuation() {
        assertEquals(true, ReviewAnswerEvaluator.evaluate(CardType.SentenceCloze, "Take care", "take care").isCorrect)
        assertEquals(false, ReviewAnswerEvaluator.evaluate(CardType.SentenceCloze, "takecare", "take care").isCorrect)
        assertEquals(false, ReviewAnswerEvaluator.evaluate(CardType.AudioToSpelling, "can't", "cant").isCorrect)
    }

    @Test fun unsupportedAndEmptyAnswersKeepExplicitResults() {
        assertNull(ReviewAnswerEvaluator.evaluate(CardType.ForeignToMeaning, "word", "word").isCorrect)
        assertEquals(false, ReviewAnswerEvaluator.evaluate(CardType.AudioToSpelling, "中文", "中文").isCorrect)
        assertEquals(false, ReviewAnswerEvaluator.evaluate(CardType.AudioToSpelling, "", "").isCorrect)
    }
}
