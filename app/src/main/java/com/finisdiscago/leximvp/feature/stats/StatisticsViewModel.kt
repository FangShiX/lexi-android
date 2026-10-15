package com.finisdiscago.leximvp.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.finisdiscago.leximvp.core.time.LocalDateBoundaries
import com.finisdiscago.leximvp.domain.model.DailyStatistics
import com.finisdiscago.leximvp.domain.repository.StatisticsRepository
import com.finisdiscago.leximvp.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StatisticsUiState(
    val statistics: DailyStatistics? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class StatisticsViewModel(
    private val repository: StatisticsRepository,
    private val settingsRepository: SettingsRepository,
    private val now: () -> Long = System::currentTimeMillis
) : ViewModel() {
    private val mutableState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.value = StatisticsUiState(isLoading = true)
            val dayBoundaries = LocalDateBoundaries.consecutiveDayStarts(now(), dayCount = 7)
            val historyBoundaries = LocalDateBoundaries.previousDayStarts(now(), dayCount = 7)
            runCatching {
                val settings = settingsRepository.settings.first()
                repository.getDailyStatistics(
                    dayStartEpochMillis = dayBoundaries[0],
                    dayEndEpochMillis = dayBoundaries[1],
                    historyDayBoundaries = historyBoundaries,
                    futureDayBoundaries = dayBoundaries,
                    dailyNewWordLimit = settings.dailyNewWordLimit
                )
            }.onSuccess {
                mutableState.value = StatisticsUiState(statistics = it, isLoading = false)
            }.onFailure {
                mutableState.value = StatisticsUiState(
                    isLoading = false,
                    errorMessage = it.message ?: "统计加载失败"
                )
            }
        }
    }

    class Factory(
        private val repository: StatisticsRepository,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StatisticsViewModel(repository, settingsRepository) as T
    }
}
