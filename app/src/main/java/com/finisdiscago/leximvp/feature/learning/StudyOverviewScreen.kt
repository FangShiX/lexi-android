package com.finisdiscago.leximvp.feature.learning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.finisdiscago.leximvp.LexiApplication
import com.finisdiscago.leximvp.core.time.LocalDateBoundaries
import com.finisdiscago.leximvp.core.ui.LexiHeroCard
import com.finisdiscago.leximvp.core.ui.LexiMetricCard
import com.finisdiscago.leximvp.core.ui.LexiSectionCard
import com.finisdiscago.leximvp.domain.model.StudyHistoryDay
import com.finisdiscago.leximvp.domain.model.UserSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

@Composable
fun StudyOverviewScreen(
    app: LexiApplication,
    onStartLearning: () -> Unit,
    onStartReview: () -> Unit,
    onOpenReading: () -> Unit,
    onOpenBooks: () -> Unit,
    onOpenStatistics: () -> Unit,
    onTaskCountChange: (Int) -> Unit = {}
) {
    var state by remember { mutableStateOf(StudyOverviewState()) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val settings by app.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = UserSettings())
    val owner = LocalLifecycleOwner.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshKey++ }
    // A queue item can become due as time passes, without a database write.
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { delay(60_000); refreshKey++ }
        }
    }
    LaunchedEffect(refreshKey, settings.dailyNewWordLimit, settings.dailyReviewLimit) {
        state = state.copy(loading = true, error = null)
        try {
            val now = System.currentTimeMillis()
            val bounds = LocalDateBoundaries.consecutiveDayStarts(now, 1)
            val learning = app.learningRepository.countAllDueWords(now, bounds[0], settings.dailyNewWordLimit)
            val reviews = app.reviewRepository.countDueCards(now, bounds[0], settings.dailyReviewLimit)
            val books = app.database.vocabularyDao().observeBooks().first()
            val history = LocalDateBoundaries.previousDayStarts(bounds[1], 7).zipWithNext { start, end ->
                val row = app.database.reviewDao().getAttemptStatistics(start, end)
                StudyHistoryDay(start, row.newWordAttempts, row.reviewAttempts, row.studyTimeMillis)
            }
            val today = app.database.reviewDao().getAttemptStatistics(bounds[0], bounds[1])
            state = StudyOverviewState(StudyOverviewSnapshot(
                hasVocabulary = books.any { it.wordCount > 0 },
                learningCount = learning, reviewCount = reviews,
                intraDayCount = app.database.reviewDao().countIntraDayLearningWords(),
                readingWordCount = app.database.articleDao().countCapturedReadingWords(),
                studiedToday = today.studiedWordCount, history = history
            ), loading = false)
            onTaskCountChange(learning + reviews)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            state = state.copy(loading = false, error = failure.message ?: "学习任务加载失败")
        }
    }
    StudyOverviewContent(state, { refreshKey++ }, onStartLearning, onStartReview, onOpenReading, onOpenBooks, onOpenStatistics)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudyOverviewContent(
    state: StudyOverviewState,
    onRefresh: () -> Unit,
    onStartLearning: () -> Unit,
    onStartReview: () -> Unit,
    onOpenReading: () -> Unit,
    onOpenBooks: () -> Unit,
    onOpenStatistics: () -> Unit
) {
    Scaffold(topBar = { com.finisdiscago.leximvp.core.ui.LexiTopBar("学习", actions = {
        IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "刷新") }
    }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onRefresh) { Text("重新加载") }
            }
            val snapshot = state.snapshot
            if (snapshot == null) {
                if (state.loading) { CircularProgressIndicator(); Text("正在加载今日任务") }
                return@Column
            }
            if (state.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            when (snapshot.page) {
                StudyHomePage.Welcome -> {
                    LexiHeroCard("开始积累", "选择你的第一本词书", "先导入词包或添加单词，再安排每日学习。")
                    Button(onClick = onOpenBooks, modifier = Modifier.fillMaxWidth()) { Text("选择词书 / 添加单词") }
                }
                StudyHomePage.Tasks -> {
                    val reviewFirst = snapshot.learningCount == 0 && snapshot.reviewCount > 0
                    LexiHeroCard("今日任务", "继续保持节奏", "新词与日内回访 ${snapshot.learningCount} 项 · 到期复习 ${snapshot.reviewCount} 项")
                    Button(
                        onClick = if (reviewFirst) onStartReview else onStartLearning,
                        enabled = !state.loading && state.error == null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
                    ) { Text(if (reviewFirst) "开始到期复习" else "开始今日学习") }
                }
                StudyHomePage.Calendar -> {
                    LexiHeroCard("今日进度", "当前到期任务已完成", "今天已学习 ${snapshot.studiedToday} 个单词。稍后到期的日内回访会自动更新。")
                    OutlinedButton(onClick = onOpenStatistics, modifier = Modifier.fillMaxWidth()) { Text("查看完整学习日历") }
                }
            }
            if (snapshot.page != StudyHomePage.Welcome) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LexiMetricCard("新词与回访", snapshot.learningCount.toString(), Modifier.weight(1f))
                    LexiMetricCard("到期复习", snapshot.reviewCount.toString(), Modifier.weight(1f))
                }
                LexiSectionCard {
                    Text("近七天", fontWeight = FontWeight.SemiBold)
                    snapshot.history.forEach { day ->
                        val date = Instant.ofEpochMilli(day.dayStartEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${date.monthValue}/${date.dayOfMonth}")
                            Text(if (day.totalAttempts > 0) "新词 ${day.newWordAttempts} · 复习 ${day.reviewAttempts}" else "暂无学习记录")
                        }
                    }
                }
            }
            LexiSectionCard {
                TaskRow(Icons.Default.CollectionsBookmark, "词库", "管理词书和每日选词计划", onOpenBooks)
                HorizontalDivider()
                TaskRow(Icons.Default.History, "长期复习", "${snapshot.reviewCount} 项当前到期任务", onStartReview)
                HorizontalDivider()
                TaskRow(Icons.Default.Schedule, "日内回访", "${snapshot.intraDayCount} 个单词仍在学习阶段", onStartLearning)
                HorizontalDivider()
                TaskRow(Icons.Default.AutoStories, "阅读生词", "${snapshot.readingWordCount} 个带出处的生词", onOpenReading)
            }
        }
    }
}

@Composable
private fun TaskRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onClick) { Text("进入") }
    }
}
