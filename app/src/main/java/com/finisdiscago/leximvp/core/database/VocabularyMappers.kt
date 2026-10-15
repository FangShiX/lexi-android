package com.finisdiscago.leximvp.core.database

import com.finisdiscago.leximvp.core.database.dao.VocabularyBookSummary
import com.finisdiscago.leximvp.core.database.entity.WordEntryEntity
import com.finisdiscago.leximvp.domain.model.VocabularyBook
import com.finisdiscago.leximvp.domain.model.WordEntry

fun VocabularyBookSummary.toDomain(): VocabularyBook = VocabularyBook(
    id = id,
    name = name,
    description = description,
    wordCount = wordCount,
    learnedWordCount = learnedWordCount,
    bookType = bookType,
    isProtected = isProtected,
    selectionEnabled = selectionEnabled,
    dailySelectionLimit = dailySelectionLimit,
    selectionPriority = selectionPriority,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
)

fun WordEntryEntity.toDomain(): WordEntry = WordEntry(
    id = id,
    bookId = bookId,
    word = word,
    normalizedWord = normalizedWord,
    meaning = meaning,
    concept = concept,
    pronunciation = pronunciation,
    exampleSentence = exampleSentence,
    exampleTranslation = exampleTranslation,
    synonyms = synonyms,
    tags = tags
        ?.split(",")
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        .orEmpty(),
    richContentJson = richContentJson,
    sourceOrder = sourceOrder,
    intakeStatus = intakeStatus,
    manualFamiliarity = manualFamiliarity,
    proficiencyScore = proficiencyScore,
    lastSeenAtEpochMillis = lastSeenAtEpochMillis
)
