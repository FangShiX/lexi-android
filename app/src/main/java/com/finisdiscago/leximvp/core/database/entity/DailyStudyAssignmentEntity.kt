package com.finisdiscago.leximvp.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_study_assignments",
    foreignKeys = [
        ForeignKey(
            entity = WordEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["wordId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = VocabularyBookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("wordId"),
        Index("bookId"),
        Index("localDate"),
        Index(value = ["localDate", "wordId"], unique = true)
    ]
)
data class DailyStudyAssignmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val localDate: String,
    val wordId: Long,
    val bookId: Long,
    val assignmentKind: String = DailyAssignmentKind.NEW_WORD,
    val assignedAtEpochMillis: Long
)

object DailyAssignmentKind {
    const val NEW_WORD = "NewWord"
    const val MANUAL = "Manual"
    const val READING = "Reading"
}
