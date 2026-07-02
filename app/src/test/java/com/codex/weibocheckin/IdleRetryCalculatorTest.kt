package com.codex.weibocheckin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class IdleRetryCalculatorTest {
    @Test
    fun schedulesNextRetryFifteenMinutesLaterBeforeDeadline() {
        val next = IdleRetryCalculator.nextRetryOrNull(
            now = LocalDateTime.of(2026, 7, 2, 10, 0),
            deadline = LocalDateTime.of(2026, 7, 2, 23, 0)
        )

        assertEquals(LocalDateTime.of(2026, 7, 2, 10, 15), next)
    }

    @Test
    fun clampsRetryToDeadlineWhenFifteenMinutesWouldPassIt() {
        val next = IdleRetryCalculator.nextRetryOrNull(
            now = LocalDateTime.of(2026, 7, 2, 22, 50),
            deadline = LocalDateTime.of(2026, 7, 2, 23, 0)
        )

        assertEquals(LocalDateTime.of(2026, 7, 2, 23, 0), next)
    }

    @Test
    fun returnsNullAtOrAfterDeadline() {
        val deadline = LocalDateTime.of(2026, 7, 2, 23, 0)

        assertNull(IdleRetryCalculator.nextRetryOrNull(deadline, deadline))
        assertNull(IdleRetryCalculator.nextRetryOrNull(deadline.plusMinutes(1), deadline))
    }
}
