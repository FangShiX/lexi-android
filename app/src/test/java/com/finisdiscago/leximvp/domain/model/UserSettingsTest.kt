package com.finisdiscago.leximvp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class UserSettingsTest {
    @Test fun oldBackupWithTightLineHeightCannotOverlapLargeText() {
        assertEquals(34, UserSettings(readerFontSizeSp = 28, readerLineHeightSp = 22).effectiveReaderLineHeightSp)
        assertEquals(44, UserSettings(readerFontSizeSp = 28, readerLineHeightSp = 44).effectiveReaderLineHeightSp)
    }

    @Test fun unsupportedThemeValueKeepsOlderSettingsUsable() {
        assertEquals(ThemeMode.Day, ThemeMode.fromStorage(null))
        assertEquals(ThemeMode.Day, ThemeMode.fromStorage("unsupported"))
    }
}
