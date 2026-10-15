package com.finisdiscago.leximvp.core.reminder

import com.finisdiscago.leximvp.domain.model.UserSettings
import com.finisdiscago.leximvp.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderReconcilerTest {
    @Test
    fun enabledReminderIsRestoredFromPersistedSettings() = runBlocking {
        val scheduler = RecordingScheduler()
        val repository = FakeSettingsRepository(
            UserSettings(reminderEnabled = true, reminderHour = 7, reminderMinute = 45)
        )

        ReminderReconciler(repository, scheduler).reconcile()

        assertEquals(7 to 45, scheduler.scheduledTime)
        assertEquals(0, scheduler.cancelCount)
    }

    @Test
    fun disabledReminderClearsAnyStaleWork() = runBlocking {
        val scheduler = RecordingScheduler()

        ReminderReconciler(
            FakeSettingsRepository(UserSettings(reminderEnabled = false)),
            scheduler
        ).reconcile()

        assertEquals(null, scheduler.scheduledTime)
        assertEquals(1, scheduler.cancelCount)
    }

    private class RecordingScheduler : ReminderScheduler {
        var scheduledTime: Pair<Int, Int>? = null
        var cancelCount = 0

        override fun schedule(hour: Int, minute: Int) {
            scheduledTime = hour to minute
        }

        override fun cancel() {
            cancelCount += 1
        }
    }

    private class FakeSettingsRepository(initial: UserSettings) : SettingsRepository {
        private val state = MutableStateFlow(initial)
        override val settings: Flow<UserSettings> = state

        override suspend fun replaceSettings(settings: UserSettings) {
            state.value = settings
        }

        override suspend fun setDailyNewWordLimit(value: Int) = Unit
        override suspend fun setDailyReviewLimit(value: Int) = Unit
        override suspend fun setAutoPlayAudio(value: Boolean) = Unit
        override suspend fun setSpeechRate(value: Float) = Unit
        override suspend fun setReminderEnabled(value: Boolean) = Unit
        override suspend fun setReminderTime(hour: Int, minute: Int) = Unit
    }
}
