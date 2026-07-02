package com.codex.weibocheckin

import java.time.LocalDateTime

object IdleRetryCalculator {
    fun nextRetryOrNull(
        now: LocalDateTime,
        deadline: LocalDateTime,
        intervalMinutes: Long = AppConstants.IDLE_RETRY_INTERVAL_MINUTES
    ): LocalDateTime? {
        if (!now.isBefore(deadline)) return null
        val next = now.plusMinutes(intervalMinutes)
        return if (next.isAfter(deadline)) deadline else next
    }
}
