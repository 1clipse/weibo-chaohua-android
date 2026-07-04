package com.codex.weibocheckin

import java.time.LocalDateTime

object RecoveryRetryPolicy {
    fun retryAtOrNull(
        todayStatus: String,
        nextRetry: String,
        idleDeadline: String,
        now: LocalDateTime = LocalDateTime.now()
    ): LocalDateTime? {
        if (todayStatus != CheckinStatus.WAITING_FOR_IDLE.name &&
            todayStatus != CheckinStatus.NEEDS_ATTENTION.name
        ) {
            return null
        }

        val deadline = idleDeadline.toLocalDateTimeOrNull() ?: return null
        if (deadline.toLocalDate() != now.toLocalDate() || !now.isBefore(deadline)) return null
        val retryAt = nextRetry.toLocalDateTimeOrNull()
            ?: return catchUpRetryAt(now, deadline)
        if (retryAt.toLocalDate() != now.toLocalDate()) return catchUpRetryAt(now, deadline)
        if (retryAt.isAfter(deadline)) return catchUpRetryAt(now, deadline)
        if (retryAt.isAfter(now)) return retryAt
        if (retryAt == deadline && now.isBefore(deadline)) return retryAt

        return catchUpRetryAt(now, deadline)
    }

    fun temporaryTestAtOrNull(
        temporaryTestAt: String,
        now: LocalDateTime = LocalDateTime.now()
    ): LocalDateTime? {
        val testAt = temporaryTestAt.toLocalDateTimeOrNull() ?: return null
        return if (testAt.isAfter(now)) testAt else null
    }
}

private fun catchUpRetryAt(now: LocalDateTime, deadline: LocalDateTime): LocalDateTime? {
    if (!now.isBefore(deadline)) return null
    val soon = now.plusMinutes(1)
    return if (soon.isBefore(deadline)) soon else deadline
}

private fun String.toLocalDateTimeOrNull(): LocalDateTime? =
    takeIf { it.isNotBlank() }?.let { value ->
        runCatching { LocalDateTime.parse(value) }.getOrNull()
    }
