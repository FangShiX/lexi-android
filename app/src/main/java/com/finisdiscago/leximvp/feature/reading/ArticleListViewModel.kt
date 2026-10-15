package com.finisdiscago.leximvp.feature.reading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.finisdiscago.leximvp.domain.model.Article
import com.finisdiscago.leximvp.domain.repository.ArticleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ArticleListUiState(
    val articles: List<Article> = emptyList(),
    val errorMessage: String? = null
)

class ArticleListViewModel(
    private val repository: ArticleRepository
) : ViewModel() {
    private val error = MutableStateFlow<String?>(null)
    val uiState: StateFlow<ArticleListUiState> = combine(
        repository.observeArticles(),
        error
    ) { articles, message ->
        ArticleListUiState(articles, message)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ArticleListUiState()
    )

    fun createArticle(
        title: String,
        content: String,
        onSuccess: (Long) -> Unit
    ) {
        viewModelScope.launch {
            runCatching { repository.createArticle(title, content) }
                .onSuccess {
                    error.value = null
                    onSuccess(it)
                }
                .onFailure { error.value = it.message ?: "文章保存失败" }
        }
    }

    fun deleteArticle(articleId: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteArticle(articleId) }
                .onFailure { error.value = it.message ?: "文章删除失败" }
        }
    }

    fun clearError() {
        error.value = null
    }

    class Factory(
        private val repository: ArticleRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ArticleListViewModel(repository) as T
    }
}
