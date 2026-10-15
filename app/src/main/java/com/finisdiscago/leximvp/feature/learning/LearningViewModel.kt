package com.finisdiscago.leximvp.feature.learning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.finisdiscago.leximvp.core.audio.AudioService
import com.finisdiscago.leximvp.core.audio.AudioAvailability
import com.finisdiscago.leximvp.core.time.LocalDateBoundaries
import com.finisdiscago.leximvp.domain.model.LearningQueueItem
import com.finisdiscago.leximvp.domain.model.LearningRating
import com.finisdiscago.leximvp.domain.repository.LearningRepository
import com.finisdiscago.leximvp.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class LearningUiState(
    val currentItem: LearningQueueItem? = null,
    val dueCount: Int = 0,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val isAnswerRevealed: Boolean = false,
    val selectedRating: LearningRating? = null,
    val audioAvailability: AudioAvailability = AudioAvailability.Initializing,
    val errorMessage: String? = null
) {
    val audioReady: Boolean get() = audioAvailability == AudioAvailability.Ready
}

class LearningViewModel(
    private val repository: LearningRepository,
    private val audioService: AudioService,
    private val settingsRepository: SettingsRepository,
    private val now: () -> Long = System::currentTimeMillis
) : ViewModel() {
    private val mutableState = MutableStateFlow(LearningUiState())
    val uiState: StateFlow<LearningUiState> = mutableState.asStateFlow()

    private val sessionId = UUID.randomUUID().toString()
    private var shownAtEpochMillis = now()
    private var dailyNewWordLimit = 20

    init {
        viewModelScope.launch {
            audioService.availability.collect { availability ->
                mutableState.value = mutableState.value.copy(audioAvailability = availability)
            }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect {
                dailyNewWordLimit = it.dailyNewWordLimit
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
                val item = repository.getNextDueWord(
                    currentTime,
                    dayStart,
                    dailyNewWordLimit
                )
                val count = repository.countDueWords(
                    currentTime,
                    dayStart,
                    dailyNewWordLimit
                )
                item to count
            }.onSuccess { (item, count) ->
                shownAtEpochMillis = now()
                mutableState.value = mutableState.value.copy(
                    currentItem = item,
                    dueCount = count,
                    isLoading = false,
                    isAnswerRevealed = false,
                    selectedRating = null
                )
            }.onFailure {
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    errorMessage = it.message ?: "无法加载学习队列"
                )
            }
        }
    }

    fun rate(rating: LearningRating) {
        val item = mutableState.value.currentItem ?: return
        if (mutableState.value.isSubmitting) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSubmitting = true)
            val reviewedAt = now()
            runCatching {
                repository.recordRating(
                    item = item,
                    rating = rating,
                    responseTimeMillis = (reviewedAt - shownAtEpochMillis).coerceAtLeast(0),
                    reviewedAtEpochMillis = reviewedAt,
                    dayStartEpochMillis = LocalDateBoundaries.dayStart(reviewedAt),
                    sessionId = sessionId
                )
            }.onSuccess {
                mutableState.value = mutableState.value.copy(
                    isSubmitting = false,
                    isAnswerRevealed = true,
                    selectedRating = rating
                )
            }.onFailure {
                mutableState.value = mutableState.value.copy(
                    isSubmitting = false,
                    errorMessage = it.message ?: "评分保存失败"
                )
            }
        }
    }

    fun continueToNext() {
        audioService.stop()
        mutableState.value = mutableState.value.copy(currentItem = null)
        refresh()
    }

    fun speakCurrentWord() {
        mutableState.value.currentItem?.word?.let(audioService::speak)
    }

    override fun onCleared() {
        audioService.stop()
        super.onCleared()
    }

    class Factory(
        private val repository: LearningRepository,
        private val audioService: AudioService,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LearningViewModel(repository, audioService, settingsRepository) as T
    }
}
