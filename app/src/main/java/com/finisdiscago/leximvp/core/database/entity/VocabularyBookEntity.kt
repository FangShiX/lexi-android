package com.finisdiscago.leximvp.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vocabulary_books",
    indices = [Index(value = ["name"], unique = true)]
)
data class VocabularyBookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val bookType: String = VocabularyBookType.USER,
    val isProtected: Boolean = false,
    val selectionEnabled: Boolean = true,
    val dailySelectionLimit: Int = 20,
    val selectionPriority: Int = 0,
    val createdOrder: Long = 0,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

object VocabularyBookType {
    const val USER = "User"
    const val IMPORTED = "Imported"
    const val SYSTEM_READING_UNKNOWNS = "SystemReadingUnknowns"
}

object SystemVocabularyBook {
    const val READING_UNKNOWNS_NAME = "阅读生词"
}
