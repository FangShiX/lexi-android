package com.finisdiscago.leximvp.feature.learning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.finisdiscago.leximvp.core.audio.AudioService
import com.finisdiscago.leximvp.core.audio.userMessage
import com.finisdiscago.leximvp.core.ui.*
import com.finisdiscago.leximvp.domain.model.LearningRating
import com.finisdiscago.leximvp.domain.model.RichWordSense
import com.finisdiscago.leximvp.domain.repository.LearningRepository
import com.finisdiscago.leximvp.domain.repository.SettingsRepository
import com.finisdiscago.leximvp.feature.vocabulary.RichSenseContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningRoute(
    repository: LearningRepository,
    audioService: AudioService,
    settingsRepository: SettingsRepository,
    autoPlayAudio: Boolean,
    onOpenReview: () -> Unit,
    onOpenContent: () -> Unit,
    onOpenWord: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LearningViewModel = viewModel(factory = LearningViewModel.Factory(repository, audioService, settingsRepository))
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.currentItem?.wordId, state.audioReady) {
        if (autoPlayAudio && state.currentItem != null && state.audioReady) viewModel.speakCurrentWord()
    }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("今日学习", style = MaterialTheme.typography.titleMedium)
                        Text("新词与日内回访", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { TextButton(onClick = onOpenReview) { Text("到期 ${state.dueCount}") } },
                actions = { IconButton(onClick = viewModel::refresh) { Icon(Icons.Default.Refresh, "刷新") } }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.currentItem == null -> EmptyLearningState(state.errorMessage, viewModel::refresh, onOpenContent, Modifier.padding(padding))
            else -> {
                val item = requireNotNull(state.currentItem)
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            progress = { ((item.attemptsToday + 1).coerceAtMost(3)) / 3f },
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${item.attemptsToday + 1}/3", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            LexiStatusPill(if (state.isAnswerRevealed) "已记录" else "先判断，再揭示")
                        }
                        Spacer(Modifier.height(56.dp))
                        Text(
                            item.word,
                            Modifier.testTag(LexiTestTags.LearningWord),
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            item.pronunciation?.takeIf(String::isNotBlank)?.let {
                                Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(enabled = state.audioReady, onClick = viewModel::speakCurrentWord) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, "播放读音")
                            }
                        }
                        state.audioAvailability.userMessage()?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(38.dp))
                        if (state.isAnswerRevealed) {
                            LearningAnswer(
                                meaning = item.meaning,
                                matchedSense = item.matchedSense,
                                exampleSentence = item.exampleSentence,
                                exampleTranslation = item.exampleTranslation,
                                rating = requireNotNull(state.selectedRating),
                                graduated = item.attemptsToday + 1 >= 3,
                                hasDetails = item.richContent != null,
                                onOpenDetails = { onOpenWord(item.wordId) }
                            )
                        } else {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text(
                                "回想它的含义，然后选择最接近的熟悉程度。",
                                Modifier.padding(top = 18.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
                        Spacer(Modifier.height(24.dp))
                    }
                    if (state.isAnswerRevealed) {
                        Button(
                            onClick = viewModel::continueToNext,
                            modifier = Modifier.fillMaxWidth().height(58.dp).testTag(LexiTestTags.LearningContinue),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("继续", style = MaterialTheme.typography.titleMedium) }
                    } else LearningRatingBar(!state.isSubmitting, viewModel::rate)
                }
            }
        }
    }
}

@Composable
private fun LearningAnswer(
    meaning: String,
    matchedSense: RichWordSense?,
    exampleSentence: String?,
    exampleTranslation: String?,
    rating: LearningRating,
    graduated: Boolean,
    hasDetails: Boolean,
    onOpenDetails: () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(meaning.ifBlank { "释义待完善" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
        if (matchedSense != null) RichSenseContent(matchedSense) else {
            exampleSentence?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            exampleTranslation?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (hasDetails) TextButton(onClick = onOpenDetails, contentPadding = PaddingValues(0.dp)) { Text("查看完整词条  ›") }
        LexiStatusPill(
            text = if (graduated) "完成日内学习，进入长期复习" else when (rating) {
                LearningRating.Mastered -> "约 60 分钟后回访"
                LearningRating.Know -> "约 30 分钟后回访"
                LearningRating.Fuzzy -> "约 15 分钟后回访"
                LearningRating.Forgot -> "约 5 分钟后回访"
            },
            highlighted = true
        )
    }
}

@Composable
private fun LearningRatingBar(enabled: Boolean, onRate: (LearningRating) -> Unit) {
    val ratings = listOf(
        Triple(LearningRating.Mastered, "熟知", "60 分钟"),
        Triple(LearningRating.Know, "认识", "30 分钟"),
        Triple(LearningRating.Fuzzy, "模糊", "15 分钟"),
        Triple(LearningRating.Forgot, "忘记", "5 分钟")
    )
    val colors = listOf(LexiTealDark, LexiTeal, LexiAmber, LexiCoral)
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        ratings.chunked(2).forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                row.forEachIndexed { columnIndex, (rating, label, interval) ->
                    Button(
                        onClick = { onRate(rating) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f).height(62.dp).then(if (rating == LearningRating.Know) Modifier.testTag(LexiTestTags.LearningRateKnow) else Modifier),
                        colors = ButtonDefaults.buttonColors(containerColor = colors[rowIndex * 2 + columnIndex], contentColor = com.finisdiscago.leximvp.core.ui.readableContentColor(colors[rowIndex * 2 + columnIndex])),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, fontWeight = FontWeight.SemiBold)
                            Text(interval, style = MaterialTheme.typography.labelSmall, color = com.finisdiscago.leximvp.core.ui.readableContentColor(colors[rowIndex * 2 + columnIndex]))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLearningState(errorMessage: String?, onRefresh: () -> Unit, onOpenContent: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LexiEmptyState(
            title = if (errorMessage == null) "当前没有到期的新词" else "学习队列加载失败",
            message = errorMessage ?: "没有到期内容。你可以从词库继续选词，或稍后完成下一轮回访。",
            primaryAction = { Button(onClick = onOpenContent) { Text("去词库选词") } },
            secondaryAction = { TextButton(onClick = onRefresh) { Text("刷新") } }
        )
    }
}
