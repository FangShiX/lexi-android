package com.finisdiscago.leximvp.domain.repository

import com.finisdiscago.leximvp.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import com.finisdiscago.leximvp.domain.model.ThemeMode

interface SettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun replaceSettings(settings: UserSettings)
    suspend fun setDailyNewWordLimit(value: Int)
    suspend fun setDailyReviewLimit(value: Int)
    suspend fun setAutoPlayAudio(value: Boolean)
    suspend fun setSpeechRate(value: Float)
    suspend fun setReminderEnabled(value: Boolean)
    suspend fun setReminderTime(hour: Int, minute: Int)
    suspend fun setThemeMode(value: ThemeMode) = replaceSettings(settings.first().copy(themeMode = value))
    suspend fun setReaderFontSize(value: Int) = replaceSettings(settings.first().copy(readerFontSizeSp = value.coerceIn(15, 28)))
    suspend fun setReaderLineHeight(value: Int) = replaceSettings(settings.first().copy(readerLineHeightSp = value.coerceIn(22, 44)))
}
