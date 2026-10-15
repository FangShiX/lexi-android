package com.finisdiscago.leximvp.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "word_forms",
    foreignKeys = [ForeignKey(
        entity = WordEntryEntity::class,
        parentColumns = ["id"],
        childColumns = ["wordId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index("wordId"),
        Index("normalizedForm"),
        Index(value = ["wordId", "normalizedForm"], unique = true)
    ]
)
data class WordFormEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wordId: Long,
    val form: String,
    val normalizedForm: String,
    val relationType: String = "inflection",
    val source: String = "local"
)
