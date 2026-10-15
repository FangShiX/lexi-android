package com.finisdiscago.leximvp.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.finisdiscago.leximvp.core.reminder.ReminderScheduler
import com.finisdiscago.leximvp.domain.model.UserSettings
import com.finisdiscago.leximvp.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {
    val settings: StateFlow<UserSettings> = repository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserSettings()
    )

    fun setDailyNewLimit(value: Int) = launch { repository.setDailyNewWordLimit(value) }
    fun setDailyReviewLimit(value: Int) = launch { repository.setDailyReviewLimit(value) }
    fun setAutoPlay(value: Boolean) = launch { repository.setAutoPlayAudio(value) }
    fun setSpeechRate(value: Float) = launch { repository.setSpeechRate(value) }
    fun setThemeMode(value: com.finisdiscago.leximvp.domain.model.ThemeMode) = launch { repository.setThemeMode(value) }
    fun setReaderFontSize(value: Int) = launch {
        repository.setReaderFontSize(value)
    }
    fun setReaderLineHeight(value: Int) = launch {
        repository.setReaderLineHeight(value)
    }

    fun setReminderEnabled(value: Boolean) = launch {
        repository.setReminderEnabled(value)
        if (value) {
            val current = repository.settings.first()
            reminderScheduler.schedule(current.reminderHour, current.reminderMinute)
        } else {
            reminderScheduler.cancel()
        }
    }

    fun setReminderTime(hour: Int, minute: Int) = launch {
        repository.setReminderTime(hour, minute)
        if (repository.settings.first().reminderEnabled) {
            reminderScheduler.schedule(hour, minute)
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    class Factory(
        private val repository: SettingsRepository,
        private val reminderScheduler: ReminderScheduler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(repository, reminderScheduler) as T
    }
}
