package com.finisdiscago.leximvp.core.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.finisdiscago.leximvp.MainActivity
import com.finisdiscago.leximvp.R
import com.finisdiscago.leximvp.core.database.LexiDatabase
import com.finisdiscago.leximvp.core.database.DailyStudyPlanner
import com.finisdiscago.leximvp.core.settings.DataStoreSettingsRepository
import com.finisdiscago.leximvp.core.time.LocalDateBoundaries
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

class LearningReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val database = LexiDatabase.getInstance(applicationContext)
        val dao = database.reviewDao()
        val now = System.currentTimeMillis()
        val settings = DataStoreSettingsRepository(applicationContext).settings.first()
        val dayStart = LocalDateBoundaries.dayStart(now)
        val localDate = Instant.ofEpochMilli(dayStart)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString()
        DailyStudyPlanner(database).ensurePlan(localDate, settings.dailyNewWordLimit)
        val newWords = dao.countDueLearningWords(
            now,
            dayStart,
            settings.dailyNewWordLimit
        )
        val reviews = dao.countDueReviewCards(now).coerceAtMost(
            (
                settings.dailyReviewLimit -
                    dao.countReviewsCompletedToday(dayStart)
                ).coerceAtLeast(0)
        )
        val permissionGranted = !(
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        )
        if (!ReminderNotificationPolicy.shouldPost(newWords + reviews, permissionGranted)) {
            return Result.success()
        }
        createChannel()
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("今日单词任务")
            .setContentText("待学 $newWords 个新词，待复习 $reviews 张卡片")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        return Result.success()
    }

    private fun createChannel() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "学习提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private companion object {
        const val CHANNEL_ID = "learning_reminders"
        const val NOTIFICATION_ID = 1001
    }
}
