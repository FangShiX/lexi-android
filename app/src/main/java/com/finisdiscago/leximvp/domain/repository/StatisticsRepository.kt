package com.finisdiscago.leximvp.domain.repository

import com.finisdiscago.leximvp.domain.model.DailyStatistics

interface StatisticsRepository {
    suspend fun getDailyStatistics(
        dayStartEpochMillis: Long,
        dayEndEpochMillis: Long,
        historyDayBoundaries: List<Long>,
        futureDayBoundaries: List<Long>,
        dailyNewWordLimit: Int
    ): DailyStatistics
}
