package com.codex.weibocheckin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class DiagnosticResetPolicyTest {
    private val now = LocalDateTime.of(2026, 7, 2, 10, 0)

    @Test
    fun resetIsBlockedWhileAutomationIsActive() {
        assertFalse(DiagnosticResetPolicy.canReset(automationActive = true))
        assertTrue(DiagnosticResetPolicy.canReset(automationActive = false))
    }

    @Test
    fun deferredStatesArePreservedWhenDiagnosticsAreReset() {
        assertTrue(
            DiagnosticResetPolicy.shouldKeepDeferredState(
                todayStatus = CheckinStatus.WAITING_FOR_IDLE.name,
                nextRetry = "",
                idleDeadline = "2026-07-02T23:00",
                now = now
            )
        )
        assertTrue(
            DiagnosticResetPolicy.shouldKeepDeferredState(
                todayStatus = CheckinStatus.NEEDS_ATTENTION.name,
                nextRetry = "2026-07-02T10:15",
                idleDeadline = "2026-07-02T23:00",
                now = now
            )
        )
        assertFalse(
            DiagnosticResetPolicy.shouldKeepDeferredState(
                todayStatus = CheckinStatus.WAITING_FOR_IDLE.name,
                nextRetry = "",
                idleDeadline = "",
                now = now
            )
        )
        assertFalse(
            DiagnosticResetPolicy.shouldKeepDeferredState(
                todayStatus = CheckinStatus.WAITING_FOR_IDLE.name,
                nextRetry = "",
                idleDeadline = "2026-07-01T23:00",
                now = now
            )
        )
        assertFalse(
            DiagnosticResetPolicy.shouldKeepDeferredState(
                todayStatus = CheckinStatus.NEEDS_ATTENTION.name,
                nextRetry = "",
                idleDeadline = "2026-07-02T23:00",
                now = now
            )
        )
        assertFalse(
            DiagnosticResetPolicy.shouldKeepDeferredState(
                todayStatus = CheckinStatus.NEEDS_ATTENTION.name,
                nextRetry = "2026-07-01T10:15",
                idleDeadline = "2026-07-01T23:00",
                now = now
            )
        )
        assertFalse(
            DiagnosticResetPolicy.shouldKeepDeferredState(
                todayStatus = CheckinStatus.NEEDS_ATTENTION.name,
                nextRetry = "2026-07-02T22:55",
                idleDeadline = "2026-07-02T23:00",
                now = LocalDateTime.of(2026, 7, 2, 23, 1)
            )
        )
        assertFalse(
            DiagnosticResetPolicy.shouldKeepDeferredState(
                todayStatus = CheckinStatus.NEEDS_ATTENTION.name,
                nextRetry = "2026-07-02T23:05",
                idleDeadline = "2026-07-02T23:00",
                now = now
            )
        )
        assertFalse(
            DiagnosticResetPolicy.shouldKeepDeferredState(
                todayStatus = CheckinStatus.NEEDS_ATTENTION.name,
                nextRetry = "not-a-time",
                idleDeadline = "2026-07-02T23:00",
                now = now
            )
        )
        assertFalse(
            DiagnosticResetPolicy.shouldKeepDeferredState(
                todayStatus = CheckinStatus.NOT_RUN.name,
                nextRetry = "",
                idleDeadline = "",
                now = now
            )
        )
        assertFalse(
            DiagnosticResetPolicy.shouldKeepDeferredState(
                todayStatus = CheckinStatus.SUCCESS.name,
                nextRetry = "",
                idleDeadline = "",
                now = now
            )
        )
        assertFalse(
            DiagnosticResetPolicy.shouldKeepDeferredState(
                todayStatus = CheckinStatus.FAILED.name,
                nextRetry = "",
                idleDeadline = "",
                now = now
            )
        )
    }
}
