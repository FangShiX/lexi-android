package com.finisdiscago.leximvp.core.reminder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderScheduleTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun schedulesLaterTimeOnSameDay() {
        val now = ZonedDateTime.of(2026, 6, 12, 20, 0, 0, 0, zone)

        val delay = ReminderSchedule.initialDelay(now, 21, 30)

        assertEquals(Duration.ofMinutes(90), delay)
    }

    @Test
    fun schedulesNextDayWhenTimeHasPassed() {
        val now = ZonedDateTime.of(2026, 6, 12, 22, 0, 0, 0, zone)

        val delay = ReminderSchedule.initialDelay(now, 21, 30)

        assertEquals(Duration.ofHours(23).plusMinutes(30), delay)
    }

    @Test
    fun exactCurrentMinuteMovesToNextDay() {
        val now = ZonedDateTime.of(2026, 6, 12, 21, 30, 0, 0, zone)

        val delay = ReminderSchedule.initialDelay(now, 21, 30)

        assertEquals(Duration.ofHours(24), delay)
    }
}
