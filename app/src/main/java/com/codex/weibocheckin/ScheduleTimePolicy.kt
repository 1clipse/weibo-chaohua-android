package com.codex.weibocheckin

object ScheduleTimePolicy {
    fun isAllowed(hour: Int, minute: Int): Boolean =
        hour in AppConstants.SCHEDULABLE_HOURS && minute in 0..59

    fun normalized(hour: Int, minute: Int): Pair<Int, Int> =
        if (isAllowed(hour, minute)) {
            hour to minute
        } else {
            AppConstants.DEFAULT_HOUR to AppConstants.DEFAULT_MINUTE
        }
}
