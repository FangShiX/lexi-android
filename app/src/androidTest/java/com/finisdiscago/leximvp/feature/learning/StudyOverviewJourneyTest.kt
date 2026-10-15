package com.finisdiscago.leximvp.feature.learning

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StudyOverviewJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun snapshot(vocabulary: Boolean = true, learning: Int = 0, reviews: Int = 0) =
        StudyOverviewSnapshot(vocabulary, learning, reviews, 0, 0, 3, emptyList())

    private fun show(state: StudyOverviewState, books: () -> Unit = {}, learn: () -> Unit = {}, review: () -> Unit = {}, stats: () -> Unit = {}) {
        compose.setContent { MaterialTheme {
            StudyOverviewContent(state, {}, learn, review, {}, books, stats)
        } }
    }

    @Test fun emptyLibraryOffersWordBookInsteadOfCompletion() {
        var opened = 0
        show(StudyOverviewState(snapshot(false), false), books = { opened++ })
        compose.onNodeWithText("选择你的第一本词书").assertIsDisplayed()
        compose.onNodeWithText("当前到期任务已完成").assertDoesNotExist()
        compose.onNodeWithText("选择词书 / 添加单词").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun reviewOnlyDayStartsReviewInsteadOfEmptyLearningQueue() {
        var learning = 0; var reviews = 0
        show(StudyOverviewState(snapshot(reviews = 4), false), learn = { learning++ }, review = { reviews++ })
        compose.onNodeWithText("开始到期复习").performClick()
        compose.runOnIdle { assertEquals(0, learning); assertEquals(1, reviews) }
    }

    @Test fun learningDayStartsLearning() {
        var opened = 0
        show(StudyOverviewState(snapshot(learning = 2, reviews = 4), false), learn = { opened++ })
        compose.onNodeWithText("开始今日学习").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun completedDayOpensCalendarStatistics() {
        var opened = 0
        show(StudyOverviewState(snapshot(), false), stats = { opened++ })
        compose.onNodeWithText("当前到期任务已完成").assertIsDisplayed()
        compose.onNodeWithText("查看完整学习日历").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun failureDoesNotClaimCompletion() {
        show(StudyOverviewState(loading = false, error = "加载失败"))
        compose.onNodeWithText("加载失败").assertIsDisplayed()
        compose.onNodeWithText("重新加载").assertIsDisplayed()
        compose.onNodeWithText("当前到期任务已完成").assertDoesNotExist()
    }

    @Test fun initialLoadingDoesNotClaimCompletion() {
        show(StudyOverviewState())
        compose.onNodeWithText("正在加载今日任务").assertIsDisplayed()
        compose.onNodeWithText("当前到期任务已完成").assertDoesNotExist()
    }
}
