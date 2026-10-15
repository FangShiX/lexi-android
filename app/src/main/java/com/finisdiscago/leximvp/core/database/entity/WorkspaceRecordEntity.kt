package com.finisdiscago.leximvp.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Versioned local documents, saved filters, layouts and exam attempts. */
@Entity(tableName = "workspace_records", indices = [Index("kind")])
data class WorkspaceRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val title: String,
    val payload: String,
    val updatedAtEpochMillis: Long
)
