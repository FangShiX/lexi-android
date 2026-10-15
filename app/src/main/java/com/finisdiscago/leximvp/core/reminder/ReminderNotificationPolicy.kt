package com.finisdiscago.leximvp.core.reminder

object ReminderNotificationPolicy {
    fun shouldPost(
        dueTaskCount: Int,
        notificationPermissionGranted: Boolean
    ): Boolean = dueTaskCount > 0 && notificationPermissionGranted
}
