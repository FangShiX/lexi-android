package com.finisdiscago.leximvp.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "article_tokens",
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WordEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["wordId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("articleId"),
        Index("wordId"),
        Index(value = ["articleId", "startOffset"], unique = true),
        Index(value = ["articleId", "normalizedText"])
    ]
)
data class ArticleTokenEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val articleId: Long,
    val rawText: String,
    val normalizedText: String,
    val startOffset: Int,
    val endOffset: Int,
    val wordId: Long? = null,
    val learningStatus: String
)
