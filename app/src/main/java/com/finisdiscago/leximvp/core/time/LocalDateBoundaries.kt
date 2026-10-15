package com.finisdiscago.leximvp.core.time

import java.time.Instant
import java.time.ZoneId

object LocalDateBoundaries {
    fun dayStart(
        epochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long = Instant.ofEpochMilli(epochMillis)
        .atZone(zoneId)
        .toLocalDate()
        .atStartOfDay(zoneId)
        .toInstant()
        .toEpochMilli()

    fun consecutiveDayStarts(
        epochMillis: Long,
        dayCount: Int,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<Long> {
        require(dayCount >= 0)
        val firstDate = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
        return (0..dayCount).map { offset ->
            firstDate.plusDays(offset.toLong())
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli()
        }
    }

    fun previousDayStarts(
        epochMillis: Long,
        dayCount: Int,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<Long> {
        require(dayCount >= 0)
        val currentDate = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
        return (dayCount downTo 0).map { offset ->
            currentDate.minusDays(offset.toLong())
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli()
        }
    }
}
