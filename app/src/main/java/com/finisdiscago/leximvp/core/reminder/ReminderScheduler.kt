package com.finisdiscago.leximvp.core.reminder

interface ReminderScheduler {
    fun schedule(hour: Int, minute: Int)
    fun cancel()
}
