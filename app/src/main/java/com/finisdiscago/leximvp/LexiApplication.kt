package com.finisdiscago.leximvp

import android.app.Application
import com.finisdiscago.leximvp.core.audio.AndroidTextToSpeechService
import com.finisdiscago.leximvp.core.audio.AudioService
import com.finisdiscago.leximvp.core.backup.LexiBackupService
import com.finisdiscago.leximvp.core.database.LexiDatabase
import com.finisdiscago.leximvp.core.database.RoomArticleRepository
import com.finisdiscago.leximvp.core.database.RoomLearningRepository
import com.finisdiscago.leximvp.core.database.RoomReviewRepository
import com.finisdiscago.leximvp.core.database.RoomStatisticsRepository
import com.finisdiscago.leximvp.core.reminder.LearningReminderScheduler
import com.finisdiscago.leximvp.core.reminder.ReminderReconciler
import com.finisdiscago.leximvp.core.settings.DataStoreSettingsRepository
import com.finisdiscago.leximvp.core.database.RoomVocabularyRepository
import com.finisdiscago.leximvp.core.database.VocabularyPackageImporter
import com.finisdiscago.leximvp.domain.repository.LearningRepository
import com.finisdiscago.leximvp.domain.repository.ArticleRepository
import com.finisdiscago.leximvp.domain.repository.ReviewRepository
import com.finisdiscago.leximvp.domain.repository.SettingsRepository
import com.finisdiscago.leximvp.domain.repository.StatisticsRepository
import com.finisdiscago.leximvp.domain.repository.VocabularyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LexiApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database by lazy { LexiDatabase.getInstance(this) }

    val vocabularyRepository: VocabularyRepository by lazy {
        RoomVocabularyRepository(database)
    }

    val vocabularyPackageImporter by lazy {
        VocabularyPackageImporter(database, cacheDir)
    }

    val documentImporter by lazy {
        com.finisdiscago.leximvp.core.document.LocalDocumentImporter(this)
    }

    val learningRepository: LearningRepository by lazy {
        RoomLearningRepository(database)
    }

    val reviewRepository: ReviewRepository by lazy {
        RoomReviewRepository(database)
    }

    val statisticsRepository: StatisticsRepository by lazy {
        RoomStatisticsRepository(database)
    }

    val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(this)
    }

    val reminderScheduler by lazy {
        LearningReminderScheduler(this)
    }

    val backupService by lazy {
        LexiBackupService(
            database = database,
            settingsRepository = settingsRepository,
            cacheDirectory = cacheDir,
            filesDirectory = filesDir,
            appVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
        )
    }

    val articleRepository: ArticleRepository by lazy {
        RoomArticleRepository(database, vocabularyRepository)
    }

    val audioService: AudioService by lazy {
        AndroidTextToSpeechService(this)
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            ReminderReconciler(settingsRepository, reminderScheduler).reconcile()
        }
    }
}
