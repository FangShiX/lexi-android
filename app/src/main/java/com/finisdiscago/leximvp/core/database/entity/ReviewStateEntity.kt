package com.finisdiscago.leximvp.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "review_states",
    foreignKeys = [
        ForeignKey(
            entity = ReviewCardEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["cardId"], unique = true),
        Index("dueAtEpochMillis"),
        Index(value = ["status", "dueAtEpochMillis"])
    ]
)
data class ReviewStateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val cardId: Long,
    val status: String,
    val dueAtEpochMillis: Long,
    val intervalDays: Double = 0.0,
    val easeFactor: Double = 2.5,
    val repetitionCount: Int = 0,
    val lapseCount: Int = 0,
    val lastReviewedAtEpochMillis: Long? = null
)
