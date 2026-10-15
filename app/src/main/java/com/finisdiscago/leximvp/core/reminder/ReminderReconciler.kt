package com.finisdiscago.leximvp.core.reminder

import com.finisdiscago.leximvp.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first

class ReminderReconciler(
    private val settingsRepository: SettingsRepository,
    private val reminderScheduler: ReminderScheduler
) {
    suspend fun reconcile() {
        val settings = settingsRepository.settings.first()
        if (settings.reminderEnabled) {
            reminderScheduler.schedule(settings.reminderHour, settings.reminderMinute)
        } else {
            reminderScheduler.cancel()
        }
    }
}
