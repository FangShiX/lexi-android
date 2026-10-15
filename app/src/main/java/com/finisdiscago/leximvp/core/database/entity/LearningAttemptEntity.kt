package com.finisdiscago.leximvp.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "learning_attempts",
    foreignKeys = [
        ForeignKey(
            entity = WordEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["wordId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ReviewCardEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("wordId"),
        Index("cardId"),
        Index("attemptedAtEpochMillis"),
        Index(value = ["wordId", "attemptedAtEpochMillis"])
    ]
)
data class LearningAttemptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val wordId: Long,
    val cardId: Long? = null,
    val rating: Int,
    val isCorrect: Boolean? = null,
    val responseTimeMillis: Long,
    val attemptKind: String,
    val attemptedAtEpochMillis: Long
)
