package com.codex.weibocheckin

import java.time.LocalDateTime

object DiagnosticResetPolicy {
    fun canReset(automationActive: Boolean): Boolean = !automationActive

    fun shouldKeepDeferredState(
        todayStatus: String,
        nextRetry: String,
        idleDeadline: String,
        now: LocalDateTime = LocalDateTime.now()
    ): Boolean {
        val retryAt = nextRetry.toLocalDateTimeOrNull()
        val deadline = idleDeadline.toLocalDateTimeOrNull()
        val hasFutureDeadline = deadline != null &&
            deadline.toLocalDate() == now.toLocalDate() &&
            now.isBefore(deadline)
        val hasDeferredRetry = retryAt != null &&
            hasFutureDeadline &&
            retryAt.toLocalDate() == now.toLocalDate() &&
            !retryAt.isAfter(deadline)
        return (todayStatus == CheckinStatus.WAITING_FOR_IDLE.name && hasFutureDeadline) ||
            (todayStatus == CheckinStatus.NEEDS_ATTENTION.name && hasDeferredRetry)
    }
}

private fun String.toLocalDateTimeOrNull(): LocalDateTime? =
    takeIf { it.isNotBlank() }?.let { value ->
        runCatching { LocalDateTime.parse(value) }.getOrNull()
    }
