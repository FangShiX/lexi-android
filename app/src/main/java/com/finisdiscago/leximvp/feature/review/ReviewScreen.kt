package com.finisdiscago.leximvp.feature.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.finisdiscago.leximvp.core.audio.AudioService
import com.finisdiscago.leximvp.core.audio.AudioAvailability
import com.finisdiscago.leximvp.core.audio.userMessage
import com.finisdiscago.leximvp.core.scheduler.ReviewRating
import com.finisdiscago.leximvp.core.ui.LexiTestTags
import com.finisdiscago.leximvp.core.ui.LexiEmptyState
import com.finisdiscago.leximvp.core.ui.LexiPageTitle
import com.finisdiscago.leximvp.core.ui.LexiAmber
import com.finisdiscago.leximvp.core.ui.LexiCoral
import com.finisdiscago.leximvp.core.ui.LexiTeal
import com.finisdiscago.leximvp.core.ui.LexiTealDark
import com.finisdiscago.leximvp.domain.model.CardType
import com.finisdiscago.leximvp.domain.model.ReviewQueueItem
import com.finisdiscago.leximvp.domain.repository.ReviewRepository
import com.finisdiscago.leximvp.domain.repository.SettingsRepository
import com.finisdiscago.leximvp.feature.vocabulary.RichSenseContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewRoute(
    repository: ReviewRepository,
    audioService: AudioService,
    settingsRepository: SettingsRepository,
    autoPlayAudio: Boolean,
    onOpenNewWords: () -> Unit,
    onOpenWord: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ReviewViewModel = viewModel(
        factory = ReviewViewModel.Factory(repository, audioService, settingsRepository)
    )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.currentItem?.cardId, state.audioReady) {
        if (autoPlayAudio && state.audioReady && viewModel.shouldAutoPlay()) {
            viewModel.speakCurrentWord()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    LexiPageTitle("到期复习", "剩余 ${state.dueCount} 张卡片")
                },
                navigationIcon = {
                    TextButton(onClick = onOpenNewWords) {
                        Text("新词")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            state.currentItem == null -> ReviewEmptyState(
                message = state.errorMessage ?: "当前没有到期卡片。",
                onRefresh = viewModel::refresh,
                onOpenNewWords = onOpenNewWords,
                modifier = Modifier.padding(innerPadding)
            )

            else -> ReviewCardContent(
                item = requireNotNull(state.currentItem),
                answer = state.answer,
                answerResult = state.answerResult?.isCorrect,
                answerRevealed = state.isAnswerRevealed,
                audioReady = state.audioReady,
                audioAvailability = state.audioAvailability,
                enabled = !state.isSubmitting,
                errorMessage = state.errorMessage,
                onAnswerChange = viewModel::updateAnswer,
                onSpeak = viewModel::speakCurrentWord,
                onReveal = viewModel::revealAnswer,
                onOpenDetails = { onOpenWord(requireNotNull(state.currentItem).wordId) },
                onRate = viewModel::rate,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
private fun ReviewCardContent(
    item: ReviewQueueItem,
    answer: String,
    answerResult: Boolean?,
    answerRevealed: Boolean,
    audioReady: Boolean,
    audioAvailability: AudioAvailability,
    enabled: Boolean,
    errorMessage: String?,
    onAnswerChange: (String) -> Unit,
    onSpeak: () -> Unit,
    onReveal: () -> Unit,
    onOpenDetails: () -> Unit,
    onRate: (ReviewRating) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    Column(
        modifier = modifier.fillMaxSize().imePadding().padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = cardTypeLabel(item.cardType),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(48.dp))
            when (item.cardType) {
                CardType.AudioToChoice, CardType.SentenceCloze, CardType.SynonymDiscrimination -> {
                    if (item.cardType == CardType.AudioToChoice) TextButton(onClick = onSpeak, enabled = audioReady) { Text("播放读音") }
                    Text(item.exercise?.prompt ?: "题目内容已失效，请编辑词条后重试")
                    if (item.exercise?.options?.isNotEmpty() == true) {
                        item.exercise.options.forEach { option ->
                            OutlinedButton(onClick = { onAnswerChange(option) }, enabled = !answerRevealed) { Text((if (answer == option) "✓ " else "") + option) }
                        }
                    } else OutlinedTextField(answer, onAnswerChange, enabled = !answerRevealed, label = { Text("输入答案") })
                }
                CardType.ForeignToMeaning -> Text(
                    item.word,
                    modifier = Modifier.testTag(LexiTestTags.ReviewPrompt),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                CardType.MeaningToForeign -> Text(
                    item.meaning,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )

                CardType.AudioToSpelling -> {
                    IconButton(enabled = audioReady, onClick = onSpeak) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "播放读音"
                        )
                    }
                    Text("听音并拼写单词")
                    audioAvailability.userMessage()?.let {
                        Text(
                            text = it,
                            modifier = Modifier.testTag(LexiTestTags.AudioFallback),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        if (audioAvailability != AudioAvailability.Initializing) {
                            Text(
                                text = "提示词：${item.word}",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    OutlinedTextField(
                        value = answer,
                        onValueChange = onAnswerChange,
                        enabled = !answerRevealed,
                        label = { Text("输入单词") },
                        singleLine = true
                    )
                }
            }
            if (answerRevealed) {
                Spacer(Modifier.height(32.dp))
                Text(
                    text = item.word,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(item.meaning, style = MaterialTheme.typography.titleLarge)
                if (answerResult != null) {
                    Text(
                        text = if (answerResult == true) "拼写正确" else "拼写不正确",
                        color = if (answerResult == true) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
                item.matchedSense?.let {
                    Spacer(Modifier.height(12.dp))
                    RichSenseContent(it)
                } ?: run {
                    item.exampleSentence?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it)
                    }
                    item.exampleTranslation?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (item.richContent != null) {
                    TextButton(onClick = onOpenDetails) {
                        Text("查看完整词条")
                    }
                }
            }
            errorMessage?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }

        if (answerRevealed) {
            ReviewRatingButtons(enabled, onRate)
        } else {
            Button(
                onClick = {
                    keyboardController?.hide()
                    onReveal()
                },
                enabled = enabled &&
                    (item.cardType != CardType.AudioToSpelling || answer.isNotBlank()),
                modifier = Modifier.fillMaxWidth().height(56.dp)
                    .testTag(LexiTestTags.ReviewReveal)
            ) {
                Text(if (item.cardType == CardType.AudioToSpelling) "提交拼写" else "显示答案")
            }
        }
    }
}

@Composable
private fun ReviewRatingButtons(
    enabled: Boolean,
    onRate: (ReviewRating) -> Unit
) {
    val ratings = listOf(
        ReviewRating.Again to "忘记\n今天",
        ReviewRating.Hard to "模糊\n稍后",
        ReviewRating.Good to "认识\n按期复习",
        ReviewRating.Easy to "熟知\n延长间隔"
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 420.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ratings.chunked(2).forEach { rowRatings ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowRatings.forEach { (rating, label) ->
                            ReviewRatingButton(
                                rating,
                                label,
                                enabled,
                                onRate,
                                Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ratings.forEach { (rating, label) ->
                    ReviewRatingButton(
                        rating,
                        label,
                        enabled,
                        onRate,
                        Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewRatingButton(
    rating: ReviewRating,
    label: String,
    enabled: Boolean,
    onRate: (ReviewRating) -> Unit,
    modifier: Modifier
) {
    Button(
        onClick = { onRate(rating) },
        enabled = enabled,
        modifier = modifier
            .height(52.dp)
            .then(
                if (rating == ReviewRating.Good) {
                    Modifier.testTag(LexiTestTags.ReviewRateGood)
                } else {
                    Modifier
                }
            ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = when (rating) {
                ReviewRating.Again -> LexiCoral
                ReviewRating.Hard -> LexiAmber
                ReviewRating.Good -> LexiTeal
                ReviewRating.Easy -> LexiTealDark
            },
            contentColor = com.finisdiscago.leximvp.core.ui.readableContentColor(when (rating) {
                ReviewRating.Again -> LexiCoral
                ReviewRating.Hard -> LexiAmber
                ReviewRating.Good -> LexiTeal
                ReviewRating.Easy -> LexiTealDark
            })
        )
    ) {
        Text(label, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ReviewEmptyState(
    message: String,
    onRefresh: () -> Unit,
    onOpenNewWords: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LexiEmptyState(
                title = "复习已完成",
                message = message,
                primaryAction = {
                    Button(onClick = onRefresh) { Text("刷新") }
                },
                secondaryAction = {
                    Button(onClick = onOpenNewWords) { Text("学习新词") }
                }
            )
        }
    }
}

private fun cardTypeLabel(type: CardType): String = when (type) {
    CardType.ForeignToMeaning -> "外语词 → 中文 / 概念"
    CardType.MeaningToForeign -> "中文 / 概念 → 外语词"
    CardType.AudioToSpelling -> "听音频 → 拼写"
    CardType.AudioToChoice -> "听音选择"
    CardType.SentenceCloze -> "例句填空"
    CardType.SynonymDiscrimination -> "近义词辨析"
}
