package com.finisdiscago.leximvp.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "word_entries",
    foreignKeys = [
        ForeignKey(
            entity = VocabularyBookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("bookId"),
        Index("normalizedWord"),
        Index(value = ["language", "normalizedWord", "homographKey"], unique = true),
        Index(value = ["stableId"], unique = true),
        Index(value = ["language", "entryKind"]),
        Index("contentStatus"),
        Index(value = ["bookId", "sourceOrder"])
    ]
)
data class WordEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val word: String,
    val normalizedWord: String,
    val meaning: String,
    val concept: String? = null,
    val pronunciation: String? = null,
    val exampleSentence: String? = null,
    val exampleTranslation: String? = null,
    val synonyms: String? = null,
    val tags: String? = null,
    val audioUri: String? = null,
    val richContentJson: String? = null,
    val stableId: String,
    val language: String = "en",
    val entryKind: String = "word",
    val contentStatus: String = "unverified",
    val homographKey: String = "",
    val sourceOrder: Int = 0,
    val intakeStatus: String = WordIntakeStatus.QUEUED,
    val queuedAtEpochMillis: Long? = null,
    val manualFamiliarity: String? = null,
    val proficiencyScore: Double = 0.0,
    val lastSeenAtEpochMillis: Long? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

object WordIntakeStatus {
    const val PENDING = "Pending"
    const val QUEUED = "Queued"
    const val LEARNING = "Learning"
    const val REVIEWING = "Reviewing"
    const val MATURE = "Mature"
}

object WordFamiliarity {
    const val UNKNOWN = "Unknown"
    const val LEARNING = "Learning"
    const val FAMILIAR = "Familiar"
    const val MASTERED = "Mastered"
}
