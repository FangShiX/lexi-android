package com.finisdiscago.leximvp.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

class WordNormalizerTest {
    @Test
    fun packagesEveryAuthorizedReferencePair() {
        val stream = checkNotNull(javaClass.getResourceAsStream("/lexicon/inflections.tsv"))
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            assertEquals(32327, lines.count())
        }
    }

    @Test
    fun usesBundledEvidenceForFormsThatSuffixRulesCannotResolve() {
        assertEquals("make", WordNormalizer.preferredLemma("Making"))
        assertEquals("knife", WordNormalizer.preferredLemma("knives"))
        assertEquals("bus", WordNormalizer.preferredLemma("buses"))
        assertEquals("lie", WordNormalizer.preferredLemma("lying"))
    }

    @Test
    fun keepsExactDictionaryAndAmbiguousFormsAheadOfLemmaCandidates() {
        assertEquals("axes", WordNormalizer.lookupCandidates("Axes").first())
        org.junit.Assert.assertTrue("axe" in WordNormalizer.lookupCandidates("axes"))
        assertEquals("news", WordNormalizer.lookupCandidates("news").first())
        assertEquals("series", WordNormalizer.lookupCandidates("series").first())
        assertEquals("", WordNormalizer.preferredLemma(""))
    }

    @Test
    fun keepsWordsEndingInDoubleS() {
        assertEquals("excess", WordNormalizer.normalize("Excess"))
    }

    @Test
    fun handlesCommonInflections() {
        assertEquals("study", WordNormalizer.normalize("studies"))
        assertEquals("study", WordNormalizer.normalize("studied"))
        assertEquals("run", WordNormalizer.normalize("running"))
        assertEquals("dog", WordNormalizer.normalize("dogs"))
    }

    @Test
    fun removesSurroundingPunctuation() {
        assertEquals("word", WordNormalizer.normalize("\"Word,\""))
    }

    @Test
    fun keepsDistinctDictionaryHeadwords() {
        assertEquals("news", WordNormalizer.normalizeHeadword("News"))
        assertEquals("evening", WordNormalizer.normalizeHeadword("evening"))
        assertEquals("goods", WordNormalizer.normalizeHeadword("goods"))
    }
}
