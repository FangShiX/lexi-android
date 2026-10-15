package com.finisdiscago.leximvp.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.finisdiscago.leximvp.core.database.entity.ArticleEntity
import com.finisdiscago.leximvp.core.database.entity.ArticleTokenEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Insert
    suspend fun insertArticle(article: ArticleEntity): Long

    @Update
    suspend fun updateArticle(article: ArticleEntity)

    @Delete
    suspend fun deleteArticle(article: ArticleEntity)

    @Query("SELECT * FROM articles ORDER BY updatedAtEpochMillis DESC")
    fun observeArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE id = :articleId")
    suspend fun getArticle(articleId: Long): ArticleEntity?

    @Query("UPDATE articles SET lastReadOffset = :offset, updatedAtEpochMillis = :timestamp WHERE id = :articleId")
    suspend fun updateReadingPosition(articleId: Long, offset: Int, timestamp: Long)

    @Query("DELETE FROM articles WHERE id = :articleId")
    suspend fun deleteArticleById(articleId: Long)

    @Insert
    suspend fun insertTokens(tokens: List<ArticleTokenEntity>)

    @Query("DELETE FROM article_tokens WHERE articleId = :articleId")
    suspend fun deleteTokens(articleId: Long)

    @Query("SELECT * FROM article_tokens WHERE articleId = :articleId ORDER BY startOffset")
    suspend fun getTokens(articleId: Long): List<ArticleTokenEntity>

    @Query("SELECT COUNT(DISTINCT wordId) FROM article_tokens WHERE wordId IS NOT NULL AND learningStatus = 'Learning'")
    suspend fun countCapturedReadingWords(): Int

    @Query(
        """
        UPDATE article_tokens
        SET learningStatus = :learningStatus
        WHERE articleId = :articleId AND normalizedText = :normalizedText
        """
    )
    suspend fun updateWordStatus(
        articleId: Long,
        normalizedText: String,
        learningStatus: String
    )

    @Query(
        """
        UPDATE article_tokens
        SET wordId = :wordId, learningStatus = :learningStatus
        WHERE articleId = :articleId AND normalizedText = :normalizedText
        """
    )
    suspend fun linkWord(
        articleId: Long,
        normalizedText: String,
        wordId: Long,
        learningStatus: String
    )

    @Transaction
    suspend fun replaceTokens(articleId: Long, tokens: List<ArticleTokenEntity>) {
        require(tokens.all { it.articleId == articleId }) {
            "All tokens must belong to article $articleId"
        }
        deleteTokens(articleId)
        insertTokens(tokens)
    }
}
