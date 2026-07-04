package com.codex.weibocheckin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CheckinDeadlinePolicyTest {
    @Test
    fun startActionCreatesDeadlineForCurrentDay() {
        val deadline = CheckinDeadlinePolicy.deadlineFor(
            now = LocalDateTime.of(2026, 7, 2, 10, 0),
            action = AppConstants.ACTION_START_CHECKIN,
            storedDeadline = LocalDateTime.of(2026, 7, 1, 23, 0)
        )

        assertEquals(LocalDateTime.of(2026, 7, 2, 23, 0), deadline)
    }

    @Test
    fun temporaryTestActionCreatesDeadlineForCurrentDay() {
        val deadline = CheckinDeadlinePolicy.deadlineFor(
            now = LocalDateTime.of(2026, 7, 2, 10, 0),
            action = AppConstants.ACTION_TEST_CHECKIN,
            storedDeadline = LocalDateTime.of(2026, 7, 1, 23, 0)
        )

        assertEquals(LocalDateTime.of(2026, 7, 2, 23, 0), deadline)
    }

    @Test
    fun retryActionsReuseStoredDeadline() {
        val storedDeadline = LocalDateTime.of(2026, 7, 2, 23, 0)
        val deadline = CheckinDeadlinePolicy.deadlineFor(
            now = LocalDateTime.of(2026, 7, 3, 0, 5),
            action = AppConstants.ACTION_RETRY_CHECKIN,
            storedDeadline = storedDeadline
        )

        assertEquals(storedDeadline, deadline)
    }

    @Test
    fun idleSignalActionsReuseStoredDeadline() {
        val storedDeadline = LocalDateTime.of(2026, 7, 2, 23, 0)
        val deadline = CheckinDeadlinePolicy.deadlineFor(
            now = LocalDateTime.of(2026, 7, 3, 0, 5),
            action = AppConstants.ACTION_IDLE_SIGNAL_CHECKIN,
            storedDeadline = storedDeadline
        )

        assertEquals(storedDeadline, deadline)
    }

    @Test
    fun missingStoredDeadlineFallsBackToCurrentDayDeadline() {
        val deadline = CheckinDeadlinePolicy.deadlineFor(
            now = LocalDateTime.of(2026, 7, 2, 12, 30),
            action = AppConstants.ACTION_RETRY_CHECKIN,
            storedDeadline = null
        )

        assertEquals(LocalDateTime.of(2026, 7, 2, 23, 0), deadline)
    }

    @Test
    fun beforeDeadlineIsStrict() {
        val deadline = LocalDateTime.of(2026, 7, 2, 23, 0)

        assertTrue(CheckinDeadlinePolicy.isBeforeDeadline(deadline.minusMinutes(1), deadline))
        assertFalse(CheckinDeadlinePolicy.isBeforeDeadline(deadline, deadline))
        assertFalse(CheckinDeadlinePolicy.isBeforeDeadline(deadline.plusMinutes(1), deadline))
    }
}
