package com.codex.weibocheckin

import java.time.LocalDateTime

object CheckinDeadlinePolicy {
    fun deadlineFor(
        now: LocalDateTime,
        action: String?,
        storedDeadline: LocalDateTime?
    ): LocalDateTime {
        return if (action == AppConstants.ACTION_START_CHECKIN ||
            action == AppConstants.ACTION_TEST_CHECKIN ||
            storedDeadline == null
        ) {
            dailyDeadlineFor(now)
        } else {
            storedDeadline
        }
    }

    fun dailyDeadlineFor(now: LocalDateTime): LocalDateTime =
        now
            .withHour(AppConstants.IDLE_DEADLINE_HOUR)
            .withMinute(AppConstants.IDLE_DEADLINE_MINUTE)
            .withSecond(0)
            .withNano(0)

    fun isBeforeDeadline(now: LocalDateTime, deadline: LocalDateTime): Boolean =
        now.isBefore(deadline)
}
