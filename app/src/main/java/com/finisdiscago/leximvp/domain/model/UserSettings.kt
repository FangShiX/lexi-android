package com.finisdiscago.leximvp.domain.model

enum class ThemeMode {
    Day, Night;

    companion object {
        fun fromStorage(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: Day
    }
}

data class UserSettings(
    val dailyNewWordLimit: Int = 20,
    val dailyReviewLimit: Int = 100,
    val autoPlayAudio: Boolean = true,
    val speechRate: Float = 0.9f,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 21,
    val reminderMinute: Int = 30,
    val readerFontSizeSp: Int = 19,
    val readerLineHeightSp: Int = 31,
    val themeMode: ThemeMode = ThemeMode.Day
) {
    val effectiveReaderLineHeightSp: Int get() = maxOf(readerLineHeightSp, readerFontSizeSp + 6)
}
