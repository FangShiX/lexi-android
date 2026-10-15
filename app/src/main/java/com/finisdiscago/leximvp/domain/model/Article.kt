package com.finisdiscago.leximvp.domain.model

data class Article(
    val id: Long,
    val title: String,
    val content: String,
    val source: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val lastReadOffset: Int = 0
)

data class ArticleToken(
    val id: Long,
    val articleId: Long,
    val rawText: String,
    val normalizedText: String,
    val startOffset: Int,
    val endOffset: Int,
    val wordId: Long?,
    val status: ArticleWordStatus,
    val meaning: String? = null,
    val pronunciation: String? = null
)

enum class ArticleWordStatus {
    Unknown,
    Unlearned,
    Learning,
    Learned,
    Known
}

data class ArticleReading(
    val article: Article,
    val tokens: List<ArticleToken>
)

data class ReadingMark(
    val id: Long,
    val articleId: Long,
    val kind: String,
    val offset: Int,
    val snippet: String,
    val note: String,
    val updatedAtEpochMillis: Long
)

data class WordMatchCandidate(val wordId: Long, val headword: String, val meaning: String)
