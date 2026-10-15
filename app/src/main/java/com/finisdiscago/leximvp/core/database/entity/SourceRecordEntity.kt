package com.finisdiscago.leximvp.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** A source is a filter/evidence record. It never owns learning progress. */
@Entity(
    tableName = "source_records",
    indices = [Index(value = ["stableId"], unique = true)]
)
data class SourceRecordEntity(
    @androidx.room.PrimaryKey val id: Long,
    val stableId: String,
    val name: String,
    val description: String = "",
    val sourceType: String = "legacy_book",
    val license: String = "",
    val metadataJson: String = "{}",
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Entity(
    tableName = "entry_sources",
    primaryKeys = ["wordId", "sourceId"],
    foreignKeys = [
        ForeignKey(
            entity = WordEntryEntity::class,
            parentColumns = ["id"], childColumns = ["wordId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SourceRecordEntity::class,
            parentColumns = ["id"], childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("wordId"),
        Index("sourceId"),
        Index(value = ["sourceId", "sourceOrder"])
    ]
)
data class EntrySourceEntity(
    val wordId: Long,
    val sourceId: Long,
    val sourceOrder: Int = 0,
    val evidenceStatus: String = "unverified",
    val addedAtEpochMillis: Long
)
