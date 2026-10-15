package com.finisdiscago.leximvp.core.text

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LemmaIntegrationTest {
    @Test
    fun packagedResourceLoadsThroughAndroidClassLoaderAndResolvesReadingTokens() {
        val resource = WordNormalizer::class.java.getResourceAsStream("/lexicon/inflections.tsv")
        assertNotNull(resource)
        resource!!.bufferedReader(Charsets.UTF_8).useLines { lines ->
            assertEquals(32327, lines.count())
        }
        val source = "Making knives, buses; lying."
        val tokens = ArticleTokenizer.tokenize(source)
        assertEquals(listOf("make", "knife", "bus", "lie"), tokens.map { it.normalizedText })
        assertEquals("axes", WordNormalizer.lookupCandidates("Axes").first())
        tokens.forEach { token ->
            assertEquals(token.rawText, source.substring(token.startOffset, token.endOffset))
        }
    }
}
