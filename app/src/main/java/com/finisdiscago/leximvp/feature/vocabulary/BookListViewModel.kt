package com.finisdiscago.leximvp.feature.vocabulary

import android.database.sqlite.SQLiteFullException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.finisdiscago.leximvp.core.database.VocabularyImportProgress
import com.finisdiscago.leximvp.core.database.VocabularyImportResult
import com.finisdiscago.leximvp.core.database.VocabularyPackageException
import com.finisdiscago.leximvp.domain.model.VocabularyBook
import com.finisdiscago.leximvp.domain.repository.VocabularyRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.FileNotFoundException
import java.io.IOException

sealed interface VocabularyImportUiState {
    data object Idle : VocabularyImportUiState
    data class Running(val progress: VocabularyImportProgress?) : VocabularyImportUiState
    data class Success(val result: VocabularyImportResult) : VocabularyImportUiState
    data class Failed(val message: String) : VocabularyImportUiState
    data object Cancelled : VocabularyImportUiState
}

data class BookListUiState(
    val books: List<VocabularyBook> = emptyList(),
    val errorMessage: String? = null,
    val importState: VocabularyImportUiState = VocabularyImportUiState.Idle
) {
    val totalVocabularyCount: Int get() = books.sumOf { it.wordCount }
    val learnedVocabularyCount: Int get() = books.sumOf { it.learnedWordCount }
}

class BookListViewModel(
    private val repository: VocabularyRepository
) : ViewModel() {
    private val errorMessage = MutableStateFlow<String?>(null)
    private val importState = MutableStateFlow<VocabularyImportUiState>(
        VocabularyImportUiState.Idle
    )
    val importUiState = importState.asStateFlow()
    private var importJob: Job? = null

    val uiState: StateFlow<BookListUiState> = combine(
        repository.observeBooks(),
        errorMessage,
        importState
    ) { books, error, currentImportState ->
        BookListUiState(
            books = books,
            errorMessage = error,
            importState = currentImportState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BookListUiState()
    )

    fun createBook(name: String, description: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            runCatching { repository.createBook(name, description) }
                .onSuccess {
                    errorMessage.value = null
                    onSuccess()
                }
                .onFailure { errorMessage.value = it.userMessage() }
        }
    }

    fun updateBook(
        bookId: Long,
        name: String,
        description: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            runCatching { repository.updateBook(bookId, name, description) }
                .onSuccess {
                    errorMessage.value = null
                    onSuccess()
                }
                .onFailure { errorMessage.value = it.userMessage() }
        }
    }

    fun deleteBook(bookId: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteBook(bookId) }
                .onFailure { errorMessage.value = it.userMessage() }
        }
    }

    fun updateBookPlan(
        bookId: Long,
        selectionEnabled: Boolean,
        dailySelectionLimit: Int,
        selectionPriority: Int,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                repository.updateBookPlan(
                    bookId,
                    selectionEnabled,
                    dailySelectionLimit,
                    selectionPriority
                )
            }.onSuccess {
                errorMessage.value = null
                onSuccess()
            }.onFailure { errorMessage.value = it.userMessage() }
        }
    }

    fun clearError() {
        errorMessage.value = null
    }

    fun startImport(
        operation: suspend (
            onProgress: suspend (VocabularyImportProgress) -> Unit
        ) -> VocabularyImportResult
    ) {
        if (importJob?.isActive == true) return
        importState.value = VocabularyImportUiState.Running(progress = null)
        importJob = viewModelScope.launch {
            try {
                val result = operation { progress ->
                    importState.value = VocabularyImportUiState.Running(progress)
                }
                importState.value = VocabularyImportUiState.Success(result)
            } catch (_: CancellationException) {
                importState.value = VocabularyImportUiState.Cancelled
            } catch (error: Exception) {
                importState.value = VocabularyImportUiState.Failed(
                    error.vocabularyImportUserMessage()
                )
            } finally {
                importJob = null
            }
        }
    }

    fun cancelImport() {
        importJob?.cancel()
    }

    fun acknowledgeImportResult() {
        if (importState.value !is VocabularyImportUiState.Running) {
            importState.value = VocabularyImportUiState.Idle
        }
    }

    class Factory(
        private val repository: VocabularyRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BookListViewModel(repository) as T
    }
}

internal fun Throwable.userMessage(): String =
    message?.takeIf(String::isNotBlank) ?: "操作失败，请重试"

internal fun Throwable.vocabularyImportUserMessage(): String = when (this) {
    is VocabularyPackageException -> message ?: "词包格式无效"
    is SQLiteFullException -> "设备存储空间不足，无法导入词包"
    is SecurityException -> "没有读取该文件的权限，请重新选择"
    is FileNotFoundException -> "所选词包已移动或不可访问"
    is IOException -> "读取词包失败，请检查文件是否完整"
    else -> message?.takeIf(String::isNotBlank) ?: "词包导入失败，请重试"
}
