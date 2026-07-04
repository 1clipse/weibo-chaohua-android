package com.codex.weibocheckin

import java.time.LocalDateTime

object IdleSignalDecision {
    fun shouldSendRetryBroadcast(
        enabled: Boolean,
        automationActive: Boolean,
        todayStatus: String,
        now: LocalDateTime,
        idleDeadline: LocalDateTime?,
        deviceState: DeviceIdleChecker.DeviceIdleState
    ): Boolean {
        val waiting = todayStatus == CheckinStatus.WAITING_FOR_IDLE.name
        val pastDeadline = idleDeadline != null && !now.isBefore(idleDeadline)
        val shouldRetryNow = IdleRetryPolicy.shouldNudgeFromIdleSignal(
            enabled = enabled,
            automationActive = automationActive,
            todayStatus = todayStatus,
            now = now,
            idleDeadline = idleDeadline
        )

        if (deviceState == DeviceIdleChecker.DeviceIdleState.LOCKED_SECURE) return false
        if (!shouldRetryNow && !(waiting && pastDeadline)) return false
        return true
    }
}
