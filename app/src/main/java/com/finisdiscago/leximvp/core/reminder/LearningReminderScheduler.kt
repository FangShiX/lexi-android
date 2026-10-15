package com.finisdiscago.leximvp.core.reminder

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class LearningReminderScheduler(
    context: Context
) : ReminderScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun schedule(hour: Int, minute: Int) {
        val delay = ReminderSchedule.initialDelay(ZonedDateTime.now(), hour, minute)
        val request = PeriodicWorkRequestBuilder<LearningReminderWorker>(
            24,
            TimeUnit.HOURS
        )
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    override fun cancel() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "daily-learning-reminder"
    }
}
