package com.finisdiscago.leximvp.core.text

data class TextToken(
    val rawText: String,
    val headwordText: String,
    val normalizedText: String,
    val startOffset: Int,
    val endOffset: Int
)

object ArticleTokenizer {
    private val wordPattern = Regex("[A-Za-z]+(?:['’][A-Za-z]+)?")

    fun tokenize(content: String): List<TextToken> =
        wordPattern.findAll(content).map { match ->
            TextToken(
                rawText = match.value,
                headwordText = WordNormalizer.normalizeHeadword(
                    match.value.replace('’', '\'')
                ),
                normalizedText = WordNormalizer.preferredLemma(match.value.replace('’', '\'')),
                startOffset = match.range.first,
                endOffset = match.range.last + 1
            )
        }.filter { it.normalizedText.isNotEmpty() }.toList()
}
