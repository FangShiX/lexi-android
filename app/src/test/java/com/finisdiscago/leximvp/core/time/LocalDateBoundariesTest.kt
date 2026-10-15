package com.finisdiscago.leximvp.core.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LocalDateBoundariesTest {
    @Test
    fun springForwardDayHasTwentyThreeHours() {
        assertDayLength(
            date = LocalDate.of(2026, 3, 8),
            zoneId = ZoneId.of("America/New_York"),
            expectedHours = 23
        )
    }

    @Test
    fun fallBackDayHasTwentyFiveHours() {
        assertDayLength(
            date = LocalDate.of(2026, 11, 1),
            zoneId = ZoneId.of("America/New_York"),
            expectedHours = 25
        )
    }

    @Test
    fun consecutiveDayStartsIncludesClosingBoundary() {
        val zone = ZoneId.of("Asia/Shanghai")
        val noon = LocalDate.of(2026, 6, 13)
            .atTime(12, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        val boundaries = LocalDateBoundaries.consecutiveDayStarts(noon, 7, zone)

        assertEquals(8, boundaries.size)
        assertEquals(LocalDate.of(2026, 6, 13), localDate(boundaries.first(), zone))
        assertEquals(LocalDate.of(2026, 6, 20), localDate(boundaries.last(), zone))
    }

    @Test
    fun previousStartsStayOnLocalMidnightAcrossDst() {
        val zone = ZoneId.of("America/New_York")
        val noon = LocalDate.of(2026, 3, 10)
            .atTime(12, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        val boundaries = LocalDateBoundaries.previousDayStarts(noon, 3, zone)

        assertEquals(LocalDate.of(2026, 3, 7), localDate(boundaries.first(), zone))
        assertEquals(LocalDate.of(2026, 3, 10), localDate(boundaries.last(), zone))
        assertTrue(boundaries.zipWithNext().all { (start, end) -> end > start })
    }

    private fun assertDayLength(date: LocalDate, zoneId: ZoneId, expectedHours: Long) {
        val noon = date.atTime(12, 0).atZone(zoneId).toInstant().toEpochMilli()
        val boundaries = LocalDateBoundaries.consecutiveDayStarts(noon, 1, zoneId)
        assertEquals(expectedHours, (boundaries[1] - boundaries[0]) / 3_600_000L)
    }

    private fun localDate(epochMillis: Long, zoneId: ZoneId): LocalDate =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
}
