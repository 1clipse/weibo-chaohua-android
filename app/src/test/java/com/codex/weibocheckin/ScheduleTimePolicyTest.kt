package com.codex.weibocheckin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleTimePolicyTest {
    @Test
    fun allowsTimesBeforeDeadlineHour() {
        assertTrue(ScheduleTimePolicy.isAllowed(0, 0))
        assertTrue(ScheduleTimePolicy.isAllowed(10, 0))
        assertTrue(ScheduleTimePolicy.isAllowed(22, 59))
    }

    @Test
    fun rejectsDeadlineHourAndInvalidMinutes() {
        assertFalse(ScheduleTimePolicy.isAllowed(23, 0))
        assertFalse(ScheduleTimePolicy.isAllowed(23, 59))
        assertFalse(ScheduleTimePolicy.isAllowed(22, 60))
        assertFalse(ScheduleTimePolicy.isAllowed(-1, 0))
    }

    @Test
    fun keepsValidTimesWhenNormalizing() {
        assertEquals(22 to 59, ScheduleTimePolicy.normalized(22, 59))
    }

    @Test
    fun normalizesLegacyDeadlineTimesToDefault() {
        assertEquals(
            AppConstants.DEFAULT_HOUR to AppConstants.DEFAULT_MINUTE,
            ScheduleTimePolicy.normalized(23, 0)
        )
    }

    @Test
    fun normalizesInvalidMinutesToDefault() {
        assertEquals(
            AppConstants.DEFAULT_HOUR to AppConstants.DEFAULT_MINUTE,
            ScheduleTimePolicy.normalized(10, 60)
        )
    }
}
