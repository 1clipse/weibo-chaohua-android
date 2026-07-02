package com.codex.weibocheckin

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ScheduleCalculatorTest {
    @Test
    fun returnsTodayWhenTimeIsStillAhead() {
        val next = ScheduleCalculator.nextDailyRun(
            LocalDateTime.of(2026, 7, 2, 9, 30),
            10,
            0
        )
        assertEquals(LocalDateTime.of(2026, 7, 2, 10, 0), next)
    }

    @Test
    fun returnsTomorrowWhenTimeAlreadyPassed() {
        val next = ScheduleCalculator.nextDailyRun(
            LocalDateTime.of(2026, 7, 2, 10, 1),
            10,
            0
        )
        assertEquals(LocalDateTime.of(2026, 7, 3, 10, 0), next)
    }
}
