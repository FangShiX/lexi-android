package com.finisdiscago.leximvp.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Corpus evidence only. Descriptive labels such as "common" never become frequency records. */
@Entity(
    tableName = "frequency_records",
    foreignKeys = [
        ForeignKey(entity = WordEntryEntity::class, parentColumns = ["id"], childColumns = ["wordId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SourceRecordEntity::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("wordId"), Index("sourceId"), Index(value = ["corpusId", "rank"]), Index(value = ["corpusId", "perMillion"])]
)
data class FrequencyRecordEntity(
    @PrimaryKey val stableId: String,
    val wordId: Long,
    val sourceId: Long,
    val corpusId: String,
    val corpusName: String,
    val corpusVersion: String = "",
    val rank: Long? = null,
    val perMillion: Double? = null,
    val band: String = "",
    val license: String = "",
    val evidenceStatus: String = "unverified",
    val updatedAtEpochMillis: Long
)
