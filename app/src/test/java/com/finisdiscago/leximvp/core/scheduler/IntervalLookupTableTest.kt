package com.finisdiscago.leximvp.core.scheduler

import org.junit.Assert.assertEquals
import org.junit.Test

class IntervalLookupTableTest {
    @Test fun recoveredRowsRespectDirectionAndDifficulty() {
        assertEquals(24, IntervalLookupTable.intervalByDay(0, 5, "1"))
        assertEquals(15, IntervalLookupTable.intervalByDay(1, 5, "1"))
        assertEquals(135, IntervalLookupTable.intervalByDay(0, 1, "1"))
        assertEquals(1, IntervalLookupTable.intervalByDay(0, 5, "2"))
    }

    @Test fun unavailableDifficultyFallsBackAndUnknownHistoryIsZero() {
        assertEquals(38, IntervalLookupTable.intervalByDay(0, 0, "1"))
        assertEquals(20, IntervalLookupTable.intervalByDay(1, 11, "1"))
        assertEquals(0, IntervalLookupTable.intervalByDay(0, 5, ""))
        assertEquals(0, IntervalLookupTable.intervalByDay(0, 5, "not-a-stage"))
    }
}
