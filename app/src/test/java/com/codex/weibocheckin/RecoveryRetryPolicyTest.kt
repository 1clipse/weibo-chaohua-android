package com.codex.weibocheckin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class RecoveryRetryPolicyTest {
    private val now = LocalDateTime.of(2026, 7, 2, 10, 0)

    @Test
    fun restoresFutureWaitingRetry() {
        assertEquals(
            LocalDateTime.of(2026, 7, 2, 10, 15),
            RecoveryRetryPolicy.retryAtOrNull(
                todayStatus = CheckinStatus.WAITING_FOR_IDLE.name,
                nextRetry = "2026-07-02T10:15",
                idleDeadline = "2026-07-02T23:00",
                now = now
            )
        )
    }

    @Test
    fun restoresNeedsAttentionRetry() {
        assertEquals(
            LocalDateTime.of(2026, 7, 2, 10, 15),
            RecoveryRetryPolicy.retryAtOrNull(
                todayStatus = CheckinStatus.NEEDS_ATTENTION.name,
                nextRetry = "2026-07-02T10:15",
                idleDeadline = "2026-07-02T23:00",
                now = now
            )
        )
    }

    @Test
    fun catchesUpMissedRetrySoonWhenDeadlineStillOpen() {
        assertEquals(
            LocalDateTime.of(2026, 7, 2, 10, 1),
            RecoveryRetryPolicy.retryAtOrNull(
                todayStatus = CheckinStatus.WAITING_FOR_IDLE.name,
                nextRetry = "2026-07-02T09:45",
                idleDeadline = "2026-07-02T23:00",
                now = now
            )
        )
    }

    @Test
    fun ignoresCompletedRetryState() {
        assertNull(
            RecoveryRetryPolicy.retryAtOrNull(
                todayStatus = CheckinStatus.SUCCESS.name,
                nextRetry = "2026-07-02T10:15",
                idleDeadline = "2026-07-02T23:00",
                now = now
            )
        )
    }

    @Test
    fun restoresDeadlineRetrySoTheDayCanBeClosed() {
        assertEquals(
            LocalDateTime.of(2026, 7, 2, 23, 0),
            RecoveryRetryPolicy.retryAtOrNull(
                todayStatus = CheckinStatus.WAITING_FOR_IDLE.name,
                nextRetry = "2026-07-02T23:00",
                idleDeadline = "2026-07-02T23:00",
                now = now
            )
        )
    }

    @Test
    fun catchesUpMissingInvalidOrStaleRetryWhenDeadlineStillOpen() {
        assertEquals(
            LocalDateTime.of(2026, 7, 2, 10, 1),
            RecoveryRetryPolicy.retryAtOrNull(
                todayStatus = CheckinStatus.WAITING_FOR_IDLE.name,
                nextRetry = "",
                idleDeadline = "2026-07-02T23:00",
                now = now
            )
        )
        assertEquals(
            LocalDateTime.of(2026, 7, 2, 10, 1),
            RecoveryRetryPolicy.retryAtOrNull(
                todayStatus = CheckinStatus.WAITING_FOR_IDLE.name,
                nextRetry = "bad",
                idleDeadline = "2026-07-02T23:00",
                now = now
            )
        )
        assertEquals(
            LocalDateTime.of(2026, 7, 2, 10, 1),
            RecoveryRetryPolicy.retryAtOrNull(
                todayStatus = CheckinStatus.WAITING_FOR_IDLE.name,
                nextRetry = "2026-07-01T10:15",
                idleDeadline = "2026-07-02T23:00",
                now = now
            )
        )
    }

    @Test
    fun ignoresRetryWhenDeadlineHasPassed() {
        assertNull(
            RecoveryRetryPolicy.retryAtOrNull(
                todayStatus = CheckinStatus.WAITING_FOR_IDLE.name,
                nextRetry = "2026-07-02T22:59",
                idleDeadline = "2026-07-02T23:00",
                now = LocalDateTime.of(2026, 7, 2, 23, 0)
            )
        )
    }

    @Test
    fun restoresFutureTemporaryTest() {
        assertEquals(
            LocalDateTime.of(2026, 7, 2, 10, 2),
            RecoveryRetryPolicy.temporaryTestAtOrNull(
                temporaryTestAt = "2026-07-02T10:02",
                now = now
            )
        )
    }

    @Test
    fun ignoresPastOrInvalidTemporaryTest() {
        assertNull(
            RecoveryRetryPolicy.temporaryTestAtOrNull(
                temporaryTestAt = "2026-07-02T09:59",
                now = now
            )
        )
        assertNull(
            RecoveryRetryPolicy.temporaryTestAtOrNull(
                temporaryTestAt = "bad",
                now = now
            )
        )
        assertNull(
            RecoveryRetryPolicy.temporaryTestAtOrNull(
                temporaryTestAt = "",
                now = now
            )
        )
    }
}
