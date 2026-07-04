package com.codex.weibocheckin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class IdleSignalDecisionTest {
    private val now = LocalDateTime.of(2026, 7, 2, 10, 1)
    private val deadline = LocalDateTime.of(2026, 7, 2, 23, 0)

    @Test
    fun sendsBroadcastWhenWaitingBeforeDeadlineAndDeviceIsIdleUnlockable() {
        assertTrue(
            decide(
                todayStatus = CheckinStatus.WAITING_FOR_IDLE.name,
                deviceState = DeviceIdleChecker.DeviceIdleState.IDLE_UNLOCKABLE
            )
        )
    }

    @Test
    fun sendsBroadcastWhenDeviceStateHasNotSettledYet() {
        assertTrue(
            decide(
                todayStatus = CheckinStatus.WAITING_FOR_IDLE.name,
                deviceState = DeviceIdleChecker.DeviceIdleState.ACTIVE
            )
        )
    }

    @Test
    fun doesNotSendBroadcastWhenDeviceIsSecureLocked() {
        assertFalse(
            decide(
                todayStatus = CheckinStatus.WAITING_FOR_IDLE.name,
                deviceState = DeviceIdleChecker.DeviceIdleState.LOCKED_SECURE
            )
        )
    }

    @Test
    fun doesNotSendBroadcastWhenDisabledRunningOrNotWaiting() {
        assertFalse(decide(enabled = false))
        assertFalse(decide(automationActive = true))
        assertFalse(decide(todayStatus = CheckinStatus.SUCCESS.name))
    }

    @Test
    fun sendsBroadcastAtDeadlineSoAlarmReceiverCanFailTheDay() {
        assertTrue(
            decide(
                now = deadline,
                todayStatus = CheckinStatus.WAITING_FOR_IDLE.name,
                deviceState = DeviceIdleChecker.DeviceIdleState.ACTIVE
            )
        )
    }

    @Test
    fun doesNotSendBroadcastWithoutDeadline() {
        assertFalse(
            decide(
                todayStatus = CheckinStatus.WAITING_FOR_IDLE.name,
                idleDeadline = null,
                deviceState = DeviceIdleChecker.DeviceIdleState.IDLE_UNLOCKABLE
            )
        )
    }

    private fun decide(
        enabled: Boolean = true,
        automationActive: Boolean = false,
        todayStatus: String = CheckinStatus.WAITING_FOR_IDLE.name,
        now: LocalDateTime = this.now,
        idleDeadline: LocalDateTime? = deadline,
        deviceState: DeviceIdleChecker.DeviceIdleState = DeviceIdleChecker.DeviceIdleState.IDLE_UNLOCKABLE
    ): Boolean =
        IdleSignalDecision.shouldSendRetryBroadcast(
            enabled = enabled,
            automationActive = automationActive,
            todayStatus = todayStatus,
            now = now,
            idleDeadline = idleDeadline,
            deviceState = deviceState
        )
}
