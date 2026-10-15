package com.finisdiscago.leximvp.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "senses",
    foreignKeys = [ForeignKey(
        entity = WordEntryEntity::class,
        parentColumns = ["id"], childColumns = ["wordId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("wordId"), Index(value = ["wordId", "displayOrder"])]
)
data class SenseEntity(
    @PrimaryKey val stableId: String,
    val wordId: Long,
    val displayOrder: Int,
    val partOfSpeech: String = "",
    val meaningZh: String = "",
    val definitionEn: String = "",
    val contextsJson: String = "[]",
    val registerJson: String = "[]",
    val semanticBoundary: String = "",
    val status: String = "unverified",
    val source: String = "",
    val active: Boolean = true
)

@Entity(
    tableName = "examples",
    foreignKeys = [ForeignKey(
        entity = WordEntryEntity::class,
        parentColumns = ["id"], childColumns = ["wordId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("wordId"), Index("senseId"), Index(value = ["senseId", "displayOrder"])]
)
data class ExampleEntity(
    @PrimaryKey val stableId: String,
    val wordId: Long,
    val senseId: String?,
    val displayOrder: Int,
    val text: String,
    val translation: String = "",
    val source: String = "",
    val status: String = "unverified",
    val active: Boolean = true
)

@Entity(
    tableName = "entry_relations",
    foreignKeys = [
        ForeignKey(
            entity = WordEntryEntity::class,
            parentColumns = ["id"], childColumns = ["sourceWordId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WordEntryEntity::class,
            parentColumns = ["id"], childColumns = ["targetWordId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("sourceWordId"), Index("targetWordId"), Index("relationType")]
)
data class EntryRelationEntity(
    @PrimaryKey val stableId: String,
    val sourceWordId: Long,
    val targetWordId: Long? = null,
    val targetText: String,
    val relationType: String,
    val direction: String = "outgoing",
    val sourceSenseId: String? = null,
    val note: String = "",
    val status: String = "unverified",
    val source: String = "",
    val active: Boolean = true
)

@Entity(
    tableName = "entry_tags",
    primaryKeys = ["wordId", "category", "value", "sourceId"],
    foreignKeys = [
        ForeignKey(entity = WordEntryEntity::class, parentColumns = ["id"], childColumns = ["wordId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SourceRecordEntity::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("wordId"), Index("sourceId"), Index(value = ["category", "value"])]
)
data class EntryTagEntity(
    val wordId: Long,
    val category: String,
    val value: String,
    val sourceId: Long,
    val status: String = "unverified"
)
