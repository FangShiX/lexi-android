package com.finisdiscago.leximvp.core.reminder

import java.time.Duration
import java.time.ZonedDateTime

object ReminderSchedule {
    fun initialDelay(
        now: ZonedDateTime,
        hour: Int,
        minute: Int
    ): Duration {
        var target = now
            .withHour(hour.coerceIn(0, 23))
            .withMinute(minute.coerceIn(0, 59))
            .withSecond(0)
            .withNano(0)
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }
        return Duration.between(now, target)
    }
}
