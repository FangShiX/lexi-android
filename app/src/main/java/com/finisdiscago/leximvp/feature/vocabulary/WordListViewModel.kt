package com.finisdiscago.leximvp.feature.vocabulary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.finisdiscago.leximvp.domain.model.VocabularyBook
import com.finisdiscago.leximvp.domain.model.WordEntry
import com.finisdiscago.leximvp.domain.repository.VocabularyRepository
import com.finisdiscago.leximvp.domain.repository.WordInput
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WordListUiState(
    val book: VocabularyBook? = null,
    val words: List<WordEntry> = emptyList(),
    val query: String = "",
    val errorMessage: String? = null,
    val sort: WordSort = WordSort.ORIGINAL
)

enum class WordSort(val label: String) {
    ORIGINAL("原始词序"),
    ALPHABETICAL("字母序"),
    FAMILIARITY("熟知程度")
}

class WordListViewModel(
    private val bookId: Long,
    private val repository: VocabularyRepository
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(WordSort.ORIGINAL)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val book = repository.observeBooks().map { books ->
        books.firstOrNull { it.id == bookId }
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    private val words = combine(
        query.flatMapLatest { repository.observeWords(bookId, it) },
        sort
    ) { currentWords, currentSort ->
        when (currentSort) {
            WordSort.ORIGINAL -> currentWords.sortedBy { it.sourceOrder }
            WordSort.ALPHABETICAL -> currentWords.sortedBy { it.normalizedWord }
            WordSort.FAMILIARITY -> currentWords.sortedWith(
                compareByDescending<WordEntry> { familiarityRank(it) }
                    .thenBy { it.sourceOrder }
            )
        }
    }

    val uiState: StateFlow<WordListUiState> = combine(
        book,
        words,
        query,
        sort,
        errorMessage
    ) { currentBook, currentWords, currentQuery, currentSort, error ->
        WordListUiState(
            book = currentBook,
            words = currentWords,
            query = currentQuery,
            errorMessage = error,
            sort = currentSort
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WordListUiState()
    )

    fun updateQuery(value: String) {
        query.value = value
    }

    fun updateSort(value: WordSort) {
        sort.value = value
    }

    fun setFamiliarity(wordId: Long, familiarity: String?) {
        viewModelScope.launch {
            runCatching { repository.setWordFamiliarity(wordId, familiarity) }
                .onFailure { errorMessage.value = it.userMessage() }
        }
    }

    fun saveWord(wordId: Long?, draft: WordDraft, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val input = draft.toInput(bookId)
            val operation = if (wordId == null) {
                suspend { repository.createWord(input) }
            } else {
                suspend {
                    repository.updateWord(wordId, input)
                    wordId
                }
            }
            runCatching { operation() }
                .onSuccess {
                    errorMessage.value = null
                    onSuccess()
                }
                .onFailure { errorMessage.value = it.userMessage() }
        }
    }

    fun deleteWord(wordId: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteWord(wordId) }
                .onFailure { errorMessage.value = it.userMessage() }
        }
    }

    fun clearError() {
        errorMessage.value = null
    }

    class Factory(
        private val bookId: Long,
        private val repository: VocabularyRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            WordListViewModel(bookId, repository) as T
    }
}

private fun familiarityRank(word: WordEntry): Double = when (word.manualFamiliarity) {
    "Mastered" -> 4.0
    "Familiar" -> 3.0
    "Learning" -> 2.0
    "Unknown" -> 0.0
    else -> word.proficiencyScore
}

data class WordDraft(
    val word: String = "",
    val meaning: String = "",
    val pronunciation: String = "",
    val exampleSentence: String = "",
    val exampleTranslation: String = "",
    val tags: String = ""
) {
    fun toInput(bookId: Long): WordInput = WordInput(
        bookId = bookId,
        word = word,
        meaning = meaning,
        pronunciation = pronunciation,
        exampleSentence = exampleSentence,
        exampleTranslation = exampleTranslation,
        tags = tags.split(",")
    )

    companion object {
        fun from(word: WordEntry): WordDraft = WordDraft(
            word = word.word,
            meaning = word.meaning,
            pronunciation = word.pronunciation.orEmpty(),
            exampleSentence = word.exampleSentence.orEmpty(),
            exampleTranslation = word.exampleTranslation.orEmpty(),
            tags = word.tags.joinToString(", ")
        )
    }
}
