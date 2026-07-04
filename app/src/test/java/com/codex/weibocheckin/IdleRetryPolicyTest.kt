package com.codex.weibocheckin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class IdleRetryPolicyTest {
    private val deadline = LocalDateTime.of(2026, 7, 2, 23, 0)

    @Test
    fun nudgesOnlyWhenWaitingForIdleBeforeDeadline() {
        assertTrue(
            IdleRetryPolicy.shouldNudgeFromIdleSignal(
                enabled = true,
                automationActive = false,
                todayStatus = CheckinStatus.WAITING_FOR_IDLE.name,
                now = LocalDateTime.of(2026, 7, 2, 10, 1),
                idleDeadline = deadline
            )
        )
    }

    @Test
    fun doesNotNudgeWhenDisabledRunningOrNotWaiting() {
        val now = LocalDateTime.of(2026, 7, 2, 10, 1)

        assertFalse(
            IdleRetryPolicy.shouldNudgeFromIdleSignal(
                enabled = false,
                automationActive = false,
                todayStatus = CheckinStatus.WAITING_FOR_IDLE.name,
                now = now,
                idleDeadline = deadline
            )
        )
        assertFalse(
            IdleRetryPolicy.shouldNudgeFromIdleSignal(
                enabled = true,
                automationActive = true,
                todayStatus = CheckinStatus.WAITING_FOR_IDLE.name,
                now = now,
                idleDeadline = deadline
            )
        )
        assertFalse(
            IdleRetryPolicy.shouldNudgeFromIdleSignal(
                enabled = true,
                automationActive = false,
                todayStatus = CheckinStatus.SUCCESS.name,
                now = now,
                idleDeadline = deadline
            )
        )
    }

    @Test
    fun doesNotNudgeAtOrAfterDeadline() {
        assertFalse(
            IdleRetryPolicy.shouldNudgeFromIdleSignal(
                enabled = true,
                automationActive = false,
                todayStatus = CheckinStatus.WAITING_FOR_IDLE.name,
                now = deadline,
                idleDeadline = deadline
            )
        )
        assertFalse(
            IdleRetryPolicy.shouldNudgeFromIdleSignal(
                enabled = true,
                automationActive = false,
                todayStatus = CheckinStatus.WAITING_FOR_IDLE.name,
                now = deadline.plusMinutes(1),
                idleDeadline = deadline
            )
        )
    }

    @Test
    fun doesNotNudgeWithoutDeadline() {
        assertFalse(
            IdleRetryPolicy.shouldNudgeFromIdleSignal(
                enabled = true,
                automationActive = false,
                todayStatus = CheckinStatus.WAITING_FOR_IDLE.name,
                now = LocalDateTime.of(2026, 7, 2, 10, 1),
                idleDeadline = null
            )
        )
    }
}
