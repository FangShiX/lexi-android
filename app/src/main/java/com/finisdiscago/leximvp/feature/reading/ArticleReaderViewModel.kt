package com.finisdiscago.leximvp.feature.reading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.finisdiscago.leximvp.core.audio.AudioService
import com.finisdiscago.leximvp.core.audio.AudioAvailability
import com.finisdiscago.leximvp.domain.model.ArticleReading
import com.finisdiscago.leximvp.domain.model.ArticleToken
import com.finisdiscago.leximvp.domain.model.VocabularyBook
import com.finisdiscago.leximvp.domain.model.ReadingMark
import com.finisdiscago.leximvp.domain.model.WordMatchCandidate
import com.finisdiscago.leximvp.domain.repository.ArticleRepository
import com.finisdiscago.leximvp.domain.repository.VocabularyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ArticleReaderUiState(
    val reading: ArticleReading? = null,
    val selectedToken: ArticleToken? = null,
    val books: List<VocabularyBook> = emptyList(),
    val capturedWordIds: Set<Long> = emptySet(),
    val marks: List<ReadingMark> = emptyList(),
    val currentOffset: Int = 0,
    val jumpOffset: Int? = null,
    val wordCandidates: List<WordMatchCandidate> = emptyList(),
    val isLoading: Boolean = true,
    val audioAvailability: AudioAvailability = AudioAvailability.Initializing,
    val errorMessage: String? = null
) {
    val audioReady: Boolean get() = audioAvailability == AudioAvailability.Ready
}

class ArticleReaderViewModel(
    private val articleId: Long,
    private val articleRepository: ArticleRepository,
    private val vocabularyRepository: VocabularyRepository,
    private val audioService: AudioService,
    private val now: () -> Long = System::currentTimeMillis
) : ViewModel() {
    private val mutableState = MutableStateFlow(ArticleReaderUiState())
    val uiState: StateFlow<ArticleReaderUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            vocabularyRepository.observeBooks().collect { books ->
                mutableState.value = mutableState.value.copy(books = books)
            }
        }
        viewModelScope.launch {
            audioService.availability.collect { availability ->
                mutableState.value = mutableState.value.copy(audioAvailability = availability)
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isLoading = true)
            runCatching { articleRepository.getReading(articleId) to articleRepository.getReadingMarks(articleId) }
                .onSuccess { (reading, marks) ->
                    val captured = reading?.tokens.orEmpty()
                        .filter { token -> token.status == com.finisdiscago.leximvp.domain.model.ArticleWordStatus.Learning }
                        .mapNotNull { token -> token.wordId }
                        .toSet()
                    mutableState.value = mutableState.value.copy(
                        reading = reading,
                        selectedToken = null,
                        capturedWordIds = captured,
                        marks = marks,
                        currentOffset = reading?.article?.lastReadOffset ?: 0,
                        isLoading = false,
                        errorMessage = null
                    )
                }
                .onFailure {
                    mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        errorMessage = it.message ?: "文章加载失败"
                    )
                }
        }
    }

    fun selectToken(token: ArticleToken) {
        mutableState.value = mutableState.value.copy(selectedToken = token, wordCandidates = emptyList())
        if (token.wordId == null) viewModelScope.launch {
            runCatching { articleRepository.findWordCandidates(token.rawText) }
                .onSuccess { mutableState.value = mutableState.value.copy(wordCandidates = it) }
        }
    }

    fun dismissToken() {
        mutableState.value = mutableState.value.copy(selectedToken = null, wordCandidates = emptyList())
    }

    fun chooseCandidate(wordId: Long) {
        val token = mutableState.value.selectedToken ?: return
        viewModelScope.launch {
            runCatching { articleRepository.linkWordCandidate(articleId, token.normalizedText, wordId) }
                .onSuccess { refresh() }
                .onFailure { mutableState.value = mutableState.value.copy(errorMessage = it.message ?: "词形关联失败") }
        }
    }

    fun speakSelected() {
        mutableState.value.selectedToken?.rawText?.let(audioService::speak)
    }

    fun markKnown() = mutateSelected { token ->
        articleRepository.markKnown(articleId, token.normalizedText)
    }

    fun markUnknown() = mutateSelected { token ->
        articleRepository.markUnknown(articleId, token.normalizedText, now())
        token.wordId?.let { mutableState.value = mutableState.value.copy(capturedWordIds = mutableState.value.capturedWordIds + it) }
    }

    fun addUnknownWord(bookId: Long, meaning: String) = mutateSelected { token ->
        val id = articleRepository.addUnknownWord(
            articleId = articleId,
            normalizedText = token.normalizedText,
            bookId = bookId,
            meaning = meaning
        )
        mutableState.value = mutableState.value.copy(capturedWordIds = mutableState.value.capturedWordIds + id)
    }

    fun addPendingWord() {
        val token = mutableState.value.selectedToken ?: return
        viewModelScope.launch {
            runCatching {
                val existing = mutableState.value.books.firstOrNull { it.name == "阅读生词" }
                val bookId = existing?.id ?: vocabularyRepository.createBook(
                    "阅读生词",
                    "从阅读中收集、等待补充释义的词条"
                )
                articleRepository.addUnknownWord(articleId, token.normalizedText, bookId, "")
            }.onSuccess { id ->
                mutableState.value = mutableState.value.copy(capturedWordIds = mutableState.value.capturedWordIds + id)
                refresh()
            }.onFailure {
                mutableState.value = mutableState.value.copy(errorMessage = it.message ?: "待完善词条创建失败")
            }
        }
    }

    fun saveReadingPosition(offset: Int) {
        mutableState.value = mutableState.value.copy(currentOffset = offset)
        viewModelScope.launch { articleRepository.updateReadingPosition(articleId, offset) }
    }

    fun correctContent(content: String) {
        viewModelScope.launch {
            runCatching { articleRepository.updateArticleContent(articleId, content) }
                .onSuccess { refresh() }
                .onFailure { mutableState.value = mutableState.value.copy(errorMessage = it.message ?: "正文校正失败") }
        }
    }

    fun addBookmark() = addMark("bookmark", "")
    fun addNote(note: String) = addMark("note", note)
    fun deleteMark(id: Long) { viewModelScope.launch { articleRepository.deleteReadingMark(id); refresh() } }
    fun jumpToMark(offset: Int) { mutableState.value = mutableState.value.copy(jumpOffset = offset) }
    fun consumeJump() { mutableState.value = mutableState.value.copy(jumpOffset = null) }

    private fun addMark(kind: String, note: String) {
        viewModelScope.launch {
            runCatching { articleRepository.addReadingMark(articleId, kind, mutableState.value.currentOffset, note) }
                .onSuccess { refresh() }
                .onFailure { mutableState.value = mutableState.value.copy(errorMessage = it.message ?: "标记保存失败") }
        }
    }

    private fun mutateSelected(operation: suspend (ArticleToken) -> Unit) {
        val token = mutableState.value.selectedToken ?: return
        viewModelScope.launch {
            runCatching { operation(token) }
                .onSuccess { refresh() }
                .onFailure {
                    mutableState.value = mutableState.value.copy(
                        errorMessage = it.message ?: "单词状态更新失败"
                    )
                }
        }
    }

    override fun onCleared() {
        audioService.stop()
        super.onCleared()
    }

    class Factory(
        private val articleId: Long,
        private val articleRepository: ArticleRepository,
        private val vocabularyRepository: VocabularyRepository,
        private val audioService: AudioService
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ArticleReaderViewModel(
                articleId,
                articleRepository,
                vocabularyRepository,
                audioService
            ) as T
    }
}
