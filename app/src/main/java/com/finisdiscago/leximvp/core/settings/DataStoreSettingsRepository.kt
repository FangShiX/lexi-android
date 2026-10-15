package com.finisdiscago.leximvp.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.finisdiscago.leximvp.domain.model.ThemeMode
import androidx.datastore.preferences.preferencesDataStore
import com.finisdiscago.leximvp.domain.model.UserSettings
import com.finisdiscago.leximvp.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "user_settings")

class DataStoreSettingsRepository(
    private val context: Context
) : SettingsRepository {
    override val settings: Flow<UserSettings> =
        context.settingsDataStore.data.map { preferences ->
            UserSettings(
                dailyNewWordLimit = preferences[DAILY_NEW_LIMIT] ?: 20,
                dailyReviewLimit = preferences[DAILY_REVIEW_LIMIT] ?: 100,
                autoPlayAudio = preferences[AUTO_PLAY] ?: true,
                speechRate = preferences[SPEECH_RATE] ?: 0.9f,
                reminderEnabled = preferences[REMINDER_ENABLED] ?: false,
                reminderHour = preferences[REMINDER_HOUR] ?: 21,
                reminderMinute = preferences[REMINDER_MINUTE] ?: 30,
                readerFontSizeSp = preferences[READER_FONT_SIZE] ?: 19,
                readerLineHeightSp = preferences[READER_LINE_HEIGHT] ?: 31,
                themeMode = ThemeMode.fromStorage(preferences[THEME_MODE])
            )
        }

    override suspend fun replaceSettings(settings: UserSettings) {
        context.settingsDataStore.edit {
            it[DAILY_NEW_LIMIT] = settings.dailyNewWordLimit.coerceIn(1, 200)
            it[DAILY_REVIEW_LIMIT] = settings.dailyReviewLimit.coerceIn(1, 1_000)
            it[AUTO_PLAY] = settings.autoPlayAudio
            it[SPEECH_RATE] = settings.speechRate.coerceIn(0.5f, 1.5f)
            it[REMINDER_ENABLED] = settings.reminderEnabled
            it[REMINDER_HOUR] = settings.reminderHour.coerceIn(0, 23)
            it[REMINDER_MINUTE] = settings.reminderMinute.coerceIn(0, 59)
            it[READER_FONT_SIZE] = settings.readerFontSizeSp.coerceIn(15, 28)
            it[READER_LINE_HEIGHT] = settings.effectiveReaderLineHeightSp.coerceIn(22, 44)
            it[THEME_MODE] = settings.themeMode.name
        }
    }

    override suspend fun setDailyNewWordLimit(value: Int) {
        context.settingsDataStore.edit { it[DAILY_NEW_LIMIT] = value.coerceIn(1, 200) }
    }

    override suspend fun setDailyReviewLimit(value: Int) {
        context.settingsDataStore.edit { it[DAILY_REVIEW_LIMIT] = value.coerceIn(1, 1_000) }
    }

    override suspend fun setAutoPlayAudio(value: Boolean) {
        context.settingsDataStore.edit { it[AUTO_PLAY] = value }
    }

    override suspend fun setSpeechRate(value: Float) {
        context.settingsDataStore.edit { it[SPEECH_RATE] = value.coerceIn(0.5f, 1.5f) }
    }

    override suspend fun setReminderEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[REMINDER_ENABLED] = value }
    }

    override suspend fun setReminderTime(hour: Int, minute: Int) {
        context.settingsDataStore.edit {
            it[REMINDER_HOUR] = hour.coerceIn(0, 23)
            it[REMINDER_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    override suspend fun setThemeMode(value: ThemeMode) {
        context.settingsDataStore.edit { it[THEME_MODE] = value.name }
    }

    override suspend fun setReaderFontSize(value: Int) {
        context.settingsDataStore.edit {
            val size = value.coerceIn(15, 28)
            it[READER_FONT_SIZE] = size
            it[READER_LINE_HEIGHT] = maxOf(it[READER_LINE_HEIGHT] ?: 31, size + 6)
        }
    }

    override suspend fun setReaderLineHeight(value: Int) {
        context.settingsDataStore.edit {
            it[READER_LINE_HEIGHT] = value.coerceIn(maxOf(22, (it[READER_FONT_SIZE] ?: 19) + 6), 44)
        }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DAILY_NEW_LIMIT = intPreferencesKey("daily_new_word_limit")
        val DAILY_REVIEW_LIMIT = intPreferencesKey("daily_review_limit")
        val AUTO_PLAY = booleanPreferencesKey("auto_play_audio")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val READER_FONT_SIZE = intPreferencesKey("reader_font_size_sp")
        val READER_LINE_HEIGHT = intPreferencesKey("reader_line_height_sp")
    }
}
