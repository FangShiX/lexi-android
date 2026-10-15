package com.finisdiscago.leximvp.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.finisdiscago.leximvp.core.ui.LexiTestTags
import com.finisdiscago.leximvp.core.ui.LexiMetricCard
import com.finisdiscago.leximvp.core.ui.LexiPageTitle
import com.finisdiscago.leximvp.core.ui.LexiHeroCard
import com.finisdiscago.leximvp.core.ui.LexiSectionCard
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.finisdiscago.leximvp.domain.model.DailyStatistics
import com.finisdiscago.leximvp.domain.repository.StatisticsRepository
import com.finisdiscago.leximvp.domain.repository.SettingsRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsRoute(
    repository: StatisticsRepository,
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier,
    viewModel: StatisticsViewModel = viewModel(
        factory = StatisticsViewModel.Factory(repository, settingsRepository)
    )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier,
        topBar = {
            com.finisdiscago.leximvp.core.ui.LexiTopBar("统计",
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.statistics == null -> StatisticsEmptyState(
                errorMessage = state.errorMessage,
                onRetry = viewModel::refresh,
                modifier = Modifier.padding(padding)
            )

            else -> StatisticsContent(
                statistics = requireNotNull(state.statistics),
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun StatisticsEmptyState(
    errorMessage: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (errorMessage == null) "暂无统计" else "统计加载失败",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = errorMessage ?: "完成一次学习或复习后，这里会显示进度。",
                color = if (errorMessage == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.testTag(LexiTestTags.StatisticsRetry)
            ) {
                Text("重新加载")
            }
        }
    }
}

@Composable
private fun StatisticsContent(
    statistics: DailyStatistics,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            LexiHeroCard(
                eyebrow = "今日学习",
                title = "${statistics.studiedWordCount} 个词",
                supportingText = "${statistics.newWordAttempts + statistics.reviewAttempts} 次学习与复习 · ${formatDuration(statistics.studyTimeMillis)}"
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LexiMetricCard("新词", statistics.newWordAttempts.toString(), Modifier.weight(1f))
                LexiMetricCard("复习", statistics.reviewAttempts.toString(), Modifier.weight(1f))
                LexiMetricCard(
                    "时长",
                    formatDuration(statistics.studyTimeMillis),
                    Modifier.weight(1f)
                )
            }
        }
        item {
            DistributionCard(statistics)
        }
        item { PerformanceCard(statistics) }
        if (statistics.weakSenses.isNotEmpty()) item { WeakSenseCard(statistics) }
        item { ExamStatisticsCard(statistics) }
        item {
            StudyHistoryCard(statistics)
        }
        item {
            ReviewLoadCard(statistics)
        }
    }
}

@Composable
private fun PerformanceCard(statistics: DailyStatistics) {
    LexiSectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("近 30 天题型表现", style = MaterialTheme.typography.titleMedium)
            if (statistics.cardTypePerformance.isEmpty()) Text("完成客观练习后显示", color = MaterialTheme.colorScheme.onSurfaceVariant)
            statistics.cardTypePerformance.forEach { item ->
                val accuracy = item.accuracy
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(cardTypeLabel(item.cardType))
                    Text(if (accuracy == null) "${item.attempts} 次" else "${(accuracy * 100).toInt()}% · ${item.answered} 题")
                }
            }
            Text("成熟卡片 ${statistics.matureCards} · 重学中 ${statistics.relearningCards}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeakSenseCard(statistics: DailyStatistics) {
    LexiSectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("薄弱义项", style = MaterialTheme.typography.titleMedium)
            statistics.weakSenses.forEach { item ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.word, fontWeight = FontWeight.SemiBold)
                    Text("${item.mistakes}/${item.attempts} 次需重学", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ExamStatisticsCard(statistics: DailyStatistics) {
    val exam = statistics.examPerformance
    LexiSectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("考试成绩", style = MaterialTheme.typography.titleMedium)
            if (exam.submittedAttempts == 0) Text("尚无已交卷记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                Text("已交卷 ${exam.submittedAttempts} 次 · 客观题 ${exam.objectiveCorrect}/${exam.objectiveAnswered}")
                Text("最近一次 ${exam.latestScore ?: 0} 分 · 累计正确率 ${((exam.accuracy ?: 0f) * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DistributionCard(statistics: DailyStatistics) {
    LexiSectionCard(
        Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = buildString {
                    append("过去七天学习量。")
                    statistics.history.forEach {
                        append("${it.dayLabel()} ${it.totalAttempts} 次；")
                    }
                }
            }
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("今日新词评分", style = MaterialTheme.typography.titleMedium)
            val values = listOf(
                Triple("熟知", statistics.masteredRatings, Color(0xFF269E8D)),
                Triple("认识", statistics.knowRatings, Color(0xFF72B9A8)),
                Triple("模糊", statistics.fuzzyRatings, Color(0xFFE89B22)),
                Triple("忘记", statistics.forgotRatings, Color(0xFFD95F3B))
            )
            val maxValue = max(1, values.maxOf { it.second })
            values.forEach { (label, value, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, modifier = Modifier.weight(0.22f))
                    Box(
                        Modifier
                            .weight(0.63f)
                            .height(12.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(value.toFloat() / maxValue)
                                .height(12.dp)
                                .background(color)
                        )
                    }
                    Text(
                        value.toString(),
                        modifier = Modifier.weight(0.15f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
private fun StudyHistoryCard(statistics: DailyStatistics) {
    LexiSectionCard(
        Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = buildString {
                    append("未来七天任务负载。")
                    statistics.futureReviewLoad.forEach {
                        append(
                            "${Instant.ofEpochMilli(it.dayStartEpochMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("E"))} " +
                                "新词 ${it.newWordCount}，复习 ${it.count}；"
                        )
                    }
                }
            }
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("过去七天学习量", style = MaterialTheme.typography.titleMedium)
            val maxValue = max(
                1,
                statistics.history.maxOfOrNull { it.totalAttempts } ?: 1
            )
            Row(
                modifier = Modifier.fillMaxWidth().height(150.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                statistics.history.forEach { day ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            day.totalAttempts.toString(),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(max(4f, 100f * day.totalAttempts / maxValue).dp),
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            if (day.reviewAttempts > 0) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(day.reviewAttempts.toFloat())
                                        .background(MaterialTheme.colorScheme.secondary)
                                )
                            }
                            if (day.newWordAttempts > 0) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(day.newWordAttempts.toFloat())
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                        Text(day.dayLabel(), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text(
                "主色为新词，辅色为复习；柱顶为当天总学习次数。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReviewLoadCard(statistics: DailyStatistics) {
    LexiSectionCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("未来七天任务负载", style = MaterialTheme.typography.titleMedium)
            val maxValue = max(
                1,
                statistics.futureReviewLoad.maxOfOrNull { it.totalCount } ?: 1
            )
            Row(
                modifier = Modifier.fillMaxWidth().height(150.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                statistics.futureReviewLoad.forEach { day ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            day.totalCount.toString(),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .height(max(4f, 100f * day.totalCount / maxValue).dp),
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            if (day.count > 0) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(day.count.toFloat())
                                        .background(MaterialTheme.colorScheme.secondary)
                                )
                            }
                            if (day.newWordCount > 0) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(day.newWordCount.toFloat())
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                        Text(
                            Instant.ofEpochMilli(day.dayStartEpochMillis)
                                .atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("E")),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            Text(
                "主色为预计新词，辅色为到期复习；预计新词按词书计划逐日扣减。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun com.finisdiscago.leximvp.domain.model.StudyHistoryDay.dayLabel(): String =
    Instant.ofEpochMilli(dayStartEpochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("E"))

private fun formatDuration(milliseconds: Long): String {
    val minutes = milliseconds / 60_000
    return if (minutes < 1) "${milliseconds / 1_000}s" else "${minutes}m"
}

private fun cardTypeLabel(value: String): String = when (value) {
    "ForeignToMeaning" -> "英到义"
    "MeaningToForeign" -> "义到英"
    "AudioToChoice" -> "听音选择"
    "AudioToSpelling" -> "听音拼写"
    "SentenceCloze" -> "例句填空"
    "SynonymDiscrimination" -> "近义辨析"
    else -> value
}
