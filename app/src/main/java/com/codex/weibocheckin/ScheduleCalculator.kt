package com.codex.weibocheckin

import java.time.LocalDateTime

object ScheduleCalculator {
    fun nextDailyRun(now: LocalDateTime, hour: Int, minute: Int): LocalDateTime {
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        return next
    }
}
