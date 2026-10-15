package com.finisdiscago.leximvp.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import com.finisdiscago.leximvp.core.audio.AudioService
import com.finisdiscago.leximvp.core.audio.AudioAvailability
import com.finisdiscago.leximvp.core.scheduler.ReviewRating
import com.finisdiscago.leximvp.core.ui.LexiTestTags
import com.finisdiscago.leximvp.domain.model.ArticleToken
import com.finisdiscago.leximvp.domain.model.ArticleWordStatus
import com.finisdiscago.leximvp.domain.model.CardType
import com.finisdiscago.leximvp.domain.model.LearningQueueItem
import com.finisdiscago.leximvp.domain.model.LearningRating
import com.finisdiscago.leximvp.domain.model.ReviewAnswerResult
import com.finisdiscago.leximvp.domain.model.ReviewQueueItem
import com.finisdiscago.leximvp.domain.model.UserSettings
import com.finisdiscago.leximvp.domain.model.VocabularyBook
import com.finisdiscago.leximvp.domain.repository.LearningRepository
import com.finisdiscago.leximvp.domain.repository.ReviewRepository
import com.finisdiscago.leximvp.domain.repository.SettingsRepository
import com.finisdiscago.leximvp.feature.learning.LearningRoute
import com.finisdiscago.leximvp.feature.learning.LearningViewModel
import com.finisdiscago.leximvp.feature.profile.DataSafetyCard
import com.finisdiscago.leximvp.feature.reading.TokenDialog
import com.finisdiscago.leximvp.feature.review.ReviewRoute
import com.finisdiscago.leximvp.feature.review.ReviewViewModel
import com.finisdiscago.leximvp.feature.vocabulary.VocabularyImportFailureDialog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CoreUserJourneyTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun learningRatingRevealsAnswerAndContinuesToEmptyQueue() {
        val repository = FakeLearningRepository()
        val audio = FakeAudioService()
        val settings = FakeSettingsRepository()
        val viewModel = LearningViewModel(
            repository = repository,
            audioService = audio,
            settingsRepository = settings,
            now = { 10_000L }
        )

        compose.setContent {
            MaterialTheme {
                LearningRoute(
                    repository = repository,
                    audioService = audio,
                    settingsRepository = settings,
                    autoPlayAudio = false,
                    onOpenReview = {},
                    onOpenContent = {},
                    viewModel = viewModel
                )
            }
        }

        compose.onNodeWithTag(LexiTestTags.LearningWord).assertIsDisplayed()
        compose.onNodeWithTag(LexiTestTags.LearningRateKnow).performClick()
        compose.onNodeWithText("适应").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(LearningRating.Know, repository.recordedRating)
        }
        compose.onNodeWithTag(LexiTestTags.LearningContinue).performClick()
        compose.onNodeWithText("当前没有到期的新词").assertIsDisplayed()
    }

    @Test
    fun reviewRevealAndGoodRatingRecordsReview() {
        val repository = FakeReviewRepository()
        val audio = FakeAudioService()
        val settings = FakeSettingsRepository()
        val viewModel = ReviewViewModel(
            repository = repository,
            audioService = audio,
            settingsRepository = settings,
            now = { 20_000L }
        )

        compose.setContent {
            MaterialTheme {
                ReviewRoute(
                    repository = repository,
                    audioService = audio,
                    settingsRepository = settings,
                    autoPlayAudio = false,
                    onOpenNewWords = {},
                    viewModel = viewModel
                )
            }
        }

        compose.onNodeWithTag(LexiTestTags.ReviewPrompt).assertIsDisplayed()
        compose.onNodeWithTag(LexiTestTags.ReviewReveal).performClick()
        compose.onNodeWithText("适应").assertIsDisplayed()
        compose.onNodeWithTag(LexiTestTags.ReviewRateGood).performClick()
        compose.onNodeWithText("当前没有到期卡片。").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(ReviewRating.Good, repository.recordedRating)
        }
    }

    @Test
    fun readingUnknownWordCanBeAddedToSelectedBook() {
        var addedBookId: Long? = null
        var addedMeaning: String? = null
        compose.setContent {
            MaterialTheme {
                TokenDialog(
                    token = ArticleToken(
                        id = 1,
                        articleId = 2,
                        rawText = "adapt",
                        normalizedText = "adapt",
                        startOffset = 0,
                        endOffset = 5,
                        wordId = null,
                        status = ArticleWordStatus.Unknown
                    ),
                    books = listOf(
                        VocabularyBook(
                            id = 7,
                            name = "测试词库",
                            description = "",
                            wordCount = 0,
                            createdAtEpochMillis = 0,
                            updatedAtEpochMillis = 0
                        )
                    ),
                    audioReady = false,
                    onDismiss = {},
                    onSpeak = {},
                    onKnown = {},
                    onUnknown = {},
                    onAddUnknown = { bookId, meaning ->
                        addedBookId = bookId
                        addedMeaning = meaning
                    }
                )
            }
        }

        compose.onNodeWithTag(LexiTestTags.ReadingMeaning).performTextInput("适应")
        compose.onNodeWithTag(LexiTestTags.ReadingAddUnknown).performClick()
        compose.runOnIdle {
            assertEquals(7L, addedBookId)
            assertEquals("适应", addedMeaning)
        }
    }

    @Test
    fun dataSafetyActionsExposeExportAndRestore() {
        var exportClicked = false
        var restoreClicked = false
        compose.setContent {
            MaterialTheme {
                DataSafetyCard(
                    enabled = true,
                    onExport = { exportClicked = true },
                    onRestore = { restoreClicked = true }
                )
            }
        }

        compose.onNodeWithTag(LexiTestTags.BackupExport).performClick()
        compose.onNodeWithTag(LexiTestTags.BackupRestore).performClick()
        compose.runOnIdle {
            assertTrue(exportClicked)
            assertTrue(restoreClicked)
        }
    }

    @Test
    fun unavailableAudioShowsFallbackAndKeepsSpellingReviewUsable() {
        val repository = FakeReviewRepository(cardType = CardType.AudioToSpelling)
        val audio = FakeAudioService(AudioAvailability.MissingLanguageData)
        val settings = FakeSettingsRepository()
        val viewModel = ReviewViewModel(
            repository = repository,
            audioService = audio,
            settingsRepository = settings,
            now = { 20_000L }
        )

        compose.setContent {
            MaterialTheme {
                ReviewRoute(
                    repository = repository,
                    audioService = audio,
                    settingsRepository = settings,
                    autoPlayAudio = true,
                    onOpenNewWords = {},
                    viewModel = viewModel
                )
            }
        }

        compose.onNodeWithTag(LexiTestTags.AudioFallback).assertIsDisplayed()
        compose.onNodeWithText("提示词：adapt").assertIsDisplayed()
        compose.onNodeWithText("输入单词").performTextInput("adapt")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag(LexiTestTags.ReviewReveal).performClick()
        compose.onNodeWithTag(LexiTestTags.ReviewRateGood).performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.runOnIdle { repository.recordedRating == ReviewRating.Good }
        }
        compose.runOnIdle {
            assertEquals(ReviewRating.Good, repository.recordedRating)
        }
    }

    @Test
    fun readingMeaningSurvivesSavedStateRestore() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MaterialTheme {
                TokenDialog(
                    token = ArticleToken(
                        id = 11,
                        articleId = 2,
                        rawText = "adapt",
                        normalizedText = "adapt",
                        startOffset = 0,
                        endOffset = 5,
                        wordId = null,
                        status = ArticleWordStatus.Unknown
                    ),
                    books = listOf(
                        VocabularyBook(
                            id = 7,
                            name = "测试词库",
                            description = "",
                            wordCount = 0,
                            createdAtEpochMillis = 0,
                            updatedAtEpochMillis = 0
                        )
                    ),
                    audioReady = false,
                    onDismiss = {},
                    onSpeak = {},
                    onKnown = {},
                    onUnknown = {},
                    onAddUnknown = { _, _ -> }
                )
            }
        }

        compose.onNodeWithTag(LexiTestTags.ReadingMeaning).performTextInput("适应")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag(LexiTestTags.ReadingMeaning).assertTextContains("适应")
    }

    @Test
    fun failedVocabularyImportOffersRetry() {
        var retried = false
        compose.setContent {
            MaterialTheme {
                VocabularyImportFailureDialog(
                    message = "文件已损坏",
                    onDismiss = {},
                    onRetry = { retried = true }
                )
            }
        }

        compose.onNodeWithText("文件已损坏").assertIsDisplayed()
        compose.onNodeWithTag(LexiTestTags.ImportRetry).performClick()
        compose.runOnIdle {
            assertTrue(retried)
        }
    }
}

private class FakeAudioService(
    initialAvailability: AudioAvailability = AudioAvailability.Ready
) : AudioService {
    override val availability: StateFlow<AudioAvailability> =
        MutableStateFlow(initialAvailability)
    override fun setSpeechRate(rate: Float) = Unit
    override fun speak(text: String) = Unit
    override fun stop() = Unit
    override fun shutdown() = Unit
}

private class FakeSettingsRepository : SettingsRepository {
    private val mutableSettings = MutableStateFlow(UserSettings())
    override val settings: Flow<UserSettings> = mutableSettings
    override suspend fun replaceSettings(settings: UserSettings) {
        mutableSettings.value = settings
    }

    override suspend fun setDailyNewWordLimit(value: Int) = Unit
    override suspend fun setDailyReviewLimit(value: Int) = Unit
    override suspend fun setAutoPlayAudio(value: Boolean) = Unit
    override suspend fun setSpeechRate(value: Float) = Unit
    override suspend fun setReminderEnabled(value: Boolean) = Unit
    override suspend fun setReminderTime(hour: Int, minute: Int) = Unit
}

private class FakeLearningRepository : LearningRepository {
    private var current: LearningQueueItem? = LearningQueueItem(
        wordId = 1,
        cardId = 2,
        word = "adapt",
        meaning = "适应",
        pronunciation = "/əˈdæpt/",
        exampleSentence = "We adapt.",
        exampleTranslation = "我们适应。",
        dueAtEpochMillis = 0,
        attemptsToday = 0
    )
    var recordedRating: LearningRating? = null

    override suspend fun getNextDueWord(
        nowEpochMillis: Long,
        dayStartEpochMillis: Long,
        dailyNewWordLimit: Int
    ): LearningQueueItem? = current

    override suspend fun countDueWords(
        nowEpochMillis: Long,
        dayStartEpochMillis: Long,
        dailyNewWordLimit: Int
    ): Int = if (current == null) 0 else 1

    override suspend fun recordRating(
        item: LearningQueueItem,
        rating: LearningRating,
        responseTimeMillis: Long,
        reviewedAtEpochMillis: Long,
        dayStartEpochMillis: Long,
        sessionId: String
    ) {
        recordedRating = rating
        current = null
    }
}

private class FakeReviewRepository(
    cardType: CardType = CardType.ForeignToMeaning
) : ReviewRepository {
    private var current: ReviewQueueItem? = ReviewQueueItem(
        cardId = 3,
        wordId = 4,
        cardType = cardType,
        word = "adapt",
        meaning = "适应",
        pronunciation = "/əˈdæpt/",
        exampleSentence = "We adapt.",
        exampleTranslation = "我们适应。",
        dueAtEpochMillis = 0,
        intervalDays = 2.0,
        easeFactor = 2.5
    )
    var recordedRating: ReviewRating? = null

    override suspend fun getNextDueCard(
        nowEpochMillis: Long,
        dayStartEpochMillis: Long,
        dailyReviewLimit: Int,
        excludedWordId: Long?
    ): ReviewQueueItem? = current

    override suspend fun countDueCards(
        nowEpochMillis: Long,
        dayStartEpochMillis: Long,
        dailyReviewLimit: Int
    ): Int = if (current == null) 0 else 1

    override fun checkAnswer(
        item: ReviewQueueItem,
        answer: String
    ): ReviewAnswerResult = ReviewAnswerResult(
        normalizedAnswer = answer.takeIf(String::isNotBlank),
        isCorrect = answer.equals(item.word, ignoreCase = true)
    )

    override suspend fun recordReview(
        item: ReviewQueueItem,
        rating: ReviewRating,
        answerResult: ReviewAnswerResult,
        responseTimeMillis: Long,
        reviewedAtEpochMillis: Long,
        sessionId: String
    ) {
        recordedRating = rating
        current = null
    }
}
