package com.finisdiscago.leximvp.feature.profile

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.finisdiscago.leximvp.MainActivity
import com.finisdiscago.leximvp.LexiApplication
import com.finisdiscago.leximvp.domain.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.After
import com.finisdiscago.leximvp.domain.model.UserSettings
import org.junit.Rule
import org.junit.Test
import java.io.File

class FrontendJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private var originalSettings: UserSettings? = null
    @Before fun dayMode() = runBlocking {
        val repository = (compose.activity.application as LexiApplication).settingsRepository
        originalSettings = repository.settings.first()
        repository.setThemeMode(ThemeMode.Day)
    }
    @After fun restoreSettings() {
        runBlocking { originalSettings?.let { (compose.activity.application as LexiApplication).settingsRepository.replaceSettings(it) } }
    }

    @Test fun profileSettingsThemeAndBackNavigation() {
        compose.onNodeWithTag("navigation_profile").performClick()
        compose.onNodeWithText("我的学习计划").assertIsDisplayed()
        capture("profile-day")
        compose.onNodeWithContentDescription("打开设置").performClick()
        compose.onNodeWithText("外观").assertIsDisplayed()
        compose.onNodeWithTag("navigation_profile").assertDoesNotExist()
        capture("settings-day")
        compose.onNodeWithText("夜间模式").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            runBlocking { (compose.activity.application as LexiApplication).settingsRepository.settings.first().themeMode == ThemeMode.Night }
        }
        capture("settings-night")
        compose.onNodeWithText("学习", useUnmergedTree = true).performClick()
        compose.onNodeWithText("每日任务").assertIsDisplayed()
        compose.onNodeWithText("数据", useUnmergedTree = true).performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("导出完整备份"))
        compose.onNodeWithText("导出完整备份").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("navigation_profile").assertIsSelected()
        capture("profile-night")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("备份与恢复"))
        compose.onNodeWithText("备份与恢复").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("导出完整备份"))
        compose.onNodeWithText("导出完整备份").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("调整学习计划"))
        compose.onNodeWithContentDescription("调整学习计划").performClick()
        compose.onNodeWithText("每日任务").assertIsDisplayed()
    }

    private fun capture(name: String) {
        val directory = File(compose.activity.getExternalFilesDir(null), "frontend-preview").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
