package com.finisdiscago.leximvp.core.settings

import androidx.test.core.app.ApplicationProvider
import com.finisdiscago.leximvp.LexiApplication
import com.finisdiscago.leximvp.domain.model.ThemeMode
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsPersistenceTest {
    @Test fun concurrentThemeAndTypographyWritesPreserveEachOther() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<LexiApplication>()
        val repository = app.settingsRepository
        val original = repository.settings.first()
        try {
            coroutineScope {
                launch { repository.setThemeMode(ThemeMode.Night) }
                launch { repository.setReaderFontSize(28) }
                launch { repository.setReaderLineHeight(22) }
            }
            val saved = DataStoreSettingsRepository(app).settings.first()
            assertEquals(ThemeMode.Night, saved.themeMode)
            assertEquals(28, saved.readerFontSizeSp)
            assertTrue(saved.readerLineHeightSp >= 34)
            assertTrue(File(app.filesDir, "datastore/user_settings.preferences_pb").isFile)
        } finally {
            repository.replaceSettings(original)
        }
    }
}
