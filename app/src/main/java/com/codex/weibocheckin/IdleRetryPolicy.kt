package com.codex.weibocheckin

import java.time.LocalDateTime

object IdleRetryPolicy {
    fun shouldNudgeFromIdleSignal(
        enabled: Boolean,
        automationActive: Boolean,
        todayStatus: String,
        now: LocalDateTime,
        idleDeadline: LocalDateTime?
    ): Boolean {
        if (!enabled || automationActive) return false
        if (todayStatus != CheckinStatus.WAITING_FOR_IDLE.name) return false
        val deadline = idleDeadline ?: return false
        return now.isBefore(deadline)
    }
}
