package com.finisdiscago.leximvp.domain.repository

import com.finisdiscago.leximvp.domain.model.VocabularyBook
import com.finisdiscago.leximvp.domain.model.WordEntry
import kotlinx.coroutines.flow.Flow

interface VocabularyRepository {
    fun observeBooks(): Flow<List<VocabularyBook>>
    suspend fun getBook(bookId: Long): VocabularyBook?
    suspend fun createBook(name: String, description: String): Long
    suspend fun updateBook(bookId: Long, name: String, description: String)
    suspend fun updateBookPlan(
        bookId: Long,
        selectionEnabled: Boolean,
        dailySelectionLimit: Int,
        selectionPriority: Int
    )
    suspend fun deleteBook(bookId: Long)

    fun observeWords(bookId: Long, query: String): Flow<List<WordEntry>>
    suspend fun getWord(wordId: Long): WordEntry?
    suspend fun createWord(input: WordInput): Long
    suspend fun updateWord(wordId: Long, input: WordInput)
    suspend fun setWordFamiliarity(wordId: Long, familiarity: String?)
    suspend fun deleteWord(wordId: Long)
}

data class WordInput(
    val bookId: Long,
    val word: String,
    val meaning: String,
    val concept: String = "",
    val pronunciation: String = "",
    val exampleSentence: String = "",
    val exampleTranslation: String = "",
    val synonyms: String = "",
    val tags: List<String> = emptyList(),
    val language: String = "en",
    val homographKey: String = ""
)

class VocabularyValidationException(message: String) : IllegalArgumentException(message)

class DuplicateWordException(word: String) :
    IllegalStateException("“$word”及其同形义编号已存在")
