package com.finisdiscago.leximvp.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.finisdiscago.leximvp.core.audio.AudioService
import com.finisdiscago.leximvp.core.audio.AudioAvailability
import com.finisdiscago.leximvp.core.scheduler.ReviewRating
import com.finisdiscago.leximvp.core.time.LocalDateBoundaries
import com.finisdiscago.leximvp.domain.model.CardType
import com.finisdiscago.leximvp.domain.model.ReviewAnswerResult
import com.finisdiscago.leximvp.domain.model.ReviewQueueItem
import com.finisdiscago.leximvp.domain.repository.ReviewRepository
import com.finisdiscago.leximvp.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ReviewUiState(
    val currentItem: ReviewQueueItem? = null,
    val dueCount: Int = 0,
    val answer: String = "",
    val answerResult: ReviewAnswerResult? = null,
    val isAnswerRevealed: Boolean = false,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val audioAvailability: AudioAvailability = AudioAvailability.Initializing,
    val errorMessage: String? = null
) {
    val audioReady: Boolean get() = audioAvailability == AudioAvailability.Ready
}

class ReviewViewModel(
    private val repository: ReviewRepository,
    private val audioService: AudioService,
    private val settingsRepository: SettingsRepository,
    private val now: () -> Long = System::currentTimeMillis
) : ViewModel() {
    private val mutableState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = mutableState.asStateFlow()
    private val sessionId = UUID.randomUUID().toString()
    private var shownAtEpochMillis = now()
    private var previousWordId: Long? = null
    private var dailyReviewLimit = 100

    init {
        viewModelScope.launch {
            audioService.availability.collect { availability ->
                mutableState.value = mutableState.value.copy(audioAvailability = availability)
            }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect {
                dailyReviewLimit = it.dailyReviewLimit
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                val currentTime = now()
                val dayStart = LocalDateBoundaries.dayStart(currentTime)
                repository.getNextDueCard(
                    currentTime,
                    dayStart,
                    dailyReviewLimit,
                    previousWordId
                ) to repository.countDueCards(
                    currentTime,
                    dayStart,
                    dailyReviewLimit
                )
            }.onSuccess { (item, count) ->
                shownAtEpochMillis = now()
                mutableState.value = ReviewUiState(
                    currentItem = item,
                    dueCount = count,
                    isLoading = false,
                    audioAvailability = mutableState.value.audioAvailability
                )
            }.onFailure {
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    errorMessage = it.message ?: "无法加载复习队列"
                )
            }
        }
    }

    fun updateAnswer(value: String) {
        mutableState.value = mutableState.value.copy(answer = value)
    }

    fun revealAnswer() {
        val item = mutableState.value.currentItem ?: return
        val result = repository.checkAnswer(item, mutableState.value.answer)
        mutableState.value = mutableState.value.copy(
            answerResult = result,
            isAnswerRevealed = true
        )
    }

    fun rate(rating: ReviewRating) {
        val item = mutableState.value.currentItem ?: return
        if (!mutableState.value.isAnswerRevealed || mutableState.value.isSubmitting) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSubmitting = true)
            val reviewedAt = now()
            runCatching {
                repository.recordReview(
                    item = item,
                    rating = rating,
                    answerResult = mutableState.value.answerResult
                        ?: ReviewAnswerResult(null, null),
                    responseTimeMillis = (reviewedAt - shownAtEpochMillis).coerceAtLeast(0),
                    reviewedAtEpochMillis = reviewedAt,
                    sessionId = sessionId
                )
            }.onSuccess {
                previousWordId = item.wordId
                mutableState.value = mutableState.value.copy(currentItem = null)
                refresh()
            }.onFailure {
                mutableState.value = mutableState.value.copy(
                    isSubmitting = false,
                    errorMessage = it.message ?: "复习结果保存失败"
                )
            }
        }
    }

    fun speakCurrentWord() {
        mutableState.value.currentItem?.word?.let(audioService::speak)
    }

    fun shouldAutoPlay(): Boolean =
        mutableState.value.currentItem?.cardType in setOf(CardType.AudioToSpelling, CardType.AudioToChoice)

    override fun onCleared() {
        audioService.stop()
        super.onCleared()
    }

    class Factory(
        private val repository: ReviewRepository,
        private val audioService: AudioService,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ReviewViewModel(repository, audioService, settingsRepository) as T
    }
}
