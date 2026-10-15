package com.finisdiscago.leximvp.core.text

object WordNormalizer {
    // Exact dictionary matches precede bundled inflection candidates.
    private val inflections: Map<String, String> by lazy {
        val stream = checkNotNull(WordNormalizer::class.java.getResourceAsStream(
            "/lexicon/inflections.tsv"
        )) { "Bundled lemma table is missing" }
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.associate { line ->
                val separator = line.indexOf('\t')
                check(separator > 0 && separator < line.lastIndex) { "Invalid bundled lemma pair" }
                line.substring(0, separator) to normalizeHeadword(line.substring(separator + 1))
            }
        }
    }

    private val irregularForms = mapOf(
        "am" to "be", "is" to "be", "are" to "be", "was" to "be", "were" to "be", "been" to "be",
        "went" to "go", "gone" to "go", "did" to "do", "done" to "do", "had" to "have",
        "made" to "make", "took" to "take", "taken" to "take", "came" to "come", "seen" to "see",
        "saw" to "see", "said" to "say", "bought" to "buy", "brought" to "bring", "thought" to "think",
        "children" to "child", "men" to "man", "women" to "woman", "people" to "person", "mice" to "mouse",
        "feet" to "foot", "teeth" to "tooth", "better" to "good", "best" to "good", "worse" to "bad", "worst" to "bad"
    )

    fun normalizeHeadword(raw: String): String =
        raw
            .trim()
            .lowercase()
            .trim('.', ',', ';', ':', '!', '?', '"', '\'', '(', ')', '[', ']')

    fun normalize(raw: String): String {
        val base = normalizeHeadword(raw)
        irregularForms[base]?.let { return it }
        return when {
            base.endsWith("ied") && base.length > 4 -> base.dropLast(3) + "y"
            base.endsWith("ies") && base.length > 4 -> base.dropLast(3) + "y"
            base.endsWith("ing") && base.length > 5 -> undouble(base.dropLast(3))
            base.endsWith("ed") && base.length > 4 -> base.dropLast(2)
            base.endsWith("s") &&
                !base.endsWith("ss") &&
                !base.endsWith("us") &&
                !base.endsWith("is") &&
                base.length > 3 -> base.dropLast(1)
            else -> base
        }
    }

    /** Exact form stays first. Derived candidates are only used when the local lexicon has evidence. */
    fun lookupCandidates(raw: String): List<String> {
        val exact = normalizeHeadword(raw.replace('’', '\''))
        if (exact.isBlank()) return emptyList()
        val candidates = linkedSetOf(exact)
        irregularForms[exact]?.let(candidates::add)
        inflections[exact]?.let(candidates::add)
        if (exact.endsWith("'s") && exact.length > 3) candidates += exact.dropLast(2)
        val normalized = normalize(exact)
        if (normalized != exact) candidates += normalized
        if (exact.endsWith("ing") && exact.length > 5) candidates += exact.dropLast(3) + "e"
        if (exact.endsWith("ed") && exact.length > 4) candidates += exact.dropLast(1)
        return candidates.toList()
    }

    fun preferredLemma(raw: String): String {
        val exact = normalizeHeadword(raw.replace('’', '\''))
        return irregularForms[exact] ?: inflections[exact] ?: normalize(exact)
    }

    private fun undouble(value: String): String =
        if (value.length >= 2 && value.last() == value[value.lastIndex - 1]) {
            value.dropLast(1)
        } else {
            value
        }
}
