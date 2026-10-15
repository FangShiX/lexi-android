package com.finisdiscago.leximvp.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "review_cards",
    foreignKeys = [
        ForeignKey(
            entity = WordEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["wordId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("wordId"),
        Index(value = ["wordId", "cardType"]),
        Index(value = ["stableId"], unique = true),
        Index("senseId"),
        Index("exampleId")
    ]
)
data class ReviewCardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val stableId: String,
    val wordId: Long,
    val cardType: String,
    val senseId: String? = null,
    val exampleId: String? = null,
    val enabled: Boolean = true,
    val createdAtEpochMillis: Long
)
