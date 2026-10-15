package com.finisdiscago.leximvp.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleTokenizerTest {
    @Test
    fun resolvesBundledInflectionsWithoutChangingSourceOffsets() {
        val content = "Making knives, buses; lying."
        val tokens = ArticleTokenizer.tokenize(content)
        assertEquals(listOf("make", "knife", "bus", "lie"), tokens.map { it.normalizedText })
        tokens.forEach { token ->
            assertEquals(token.rawText, content.substring(token.startOffset, token.endOffset))
        }
    }

    @Test
    fun preservesOffsetsAcrossPunctuationAndNewlines() {
        val content = "Excess, access.\nDon't guess."

        val tokens = ArticleTokenizer.tokenize(content)

        assertEquals(listOf("Excess", "access", "Don't", "guess"), tokens.map { it.rawText })
        assertEquals(listOf("excess", "access", "don't", "guess"), tokens.map { it.normalizedText })
        tokens.forEach { token ->
            assertEquals(token.rawText, content.substring(token.startOffset, token.endOffset))
        }
    }

    @Test
    fun returnsEveryOccurrenceWithIndependentOffsets() {
        val tokens = ArticleTokenizer.tokenize("word word")

        assertEquals(2, tokens.size)
        assertEquals(0, tokens[0].startOffset)
        assertEquals(5, tokens[1].startOffset)
    }
}
