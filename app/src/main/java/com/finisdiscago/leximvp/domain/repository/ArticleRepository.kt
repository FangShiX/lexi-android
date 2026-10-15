package com.finisdiscago.leximvp.domain.repository

import com.finisdiscago.leximvp.domain.model.Article
import com.finisdiscago.leximvp.domain.model.ArticleReading
import com.finisdiscago.leximvp.domain.model.ReadingMark
import com.finisdiscago.leximvp.domain.model.WordMatchCandidate
import kotlinx.coroutines.flow.Flow

interface ArticleRepository {
    fun observeArticles(): Flow<List<Article>>
    suspend fun createArticle(title: String, content: String, source: String = ""): Long
    suspend fun deleteArticle(articleId: Long)
    suspend fun getReading(articleId: Long): ArticleReading?
    suspend fun updateReadingPosition(articleId: Long, offset: Int)
    suspend fun updateArticleContent(articleId: Long, content: String)
    suspend fun getReadingMarks(articleId: Long): List<ReadingMark>
    suspend fun addReadingMark(articleId: Long, kind: String, offset: Int, note: String = ""): Long
    suspend fun deleteReadingMark(markId: Long)
    suspend fun findWordCandidates(text: String): List<WordMatchCandidate>
    suspend fun linkWordCandidate(articleId: Long, normalizedText: String, wordId: Long)
    suspend fun markKnown(articleId: Long, normalizedText: String)
    suspend fun markUnknown(articleId: Long, normalizedText: String, nowEpochMillis: Long)
    suspend fun addUnknownWord(
        articleId: Long,
        normalizedText: String,
        bookId: Long,
        meaning: String
    ): Long
}
