package com.codex.weibocheckin

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceIdleCheckerTest {
    @Test
    fun interactiveUnlockedDeviceIsActive() {
        assertEquals(
            DeviceIdleChecker.DeviceIdleState.ACTIVE,
            DeviceIdleChecker.stateFrom(
                isInteractive = true,
                isKeyguardLocked = false,
                isDeviceSecure = false
            )
        )
    }

    @Test
    fun screenOffDeviceIsIdleUnlockable() {
        assertEquals(
            DeviceIdleChecker.DeviceIdleState.IDLE_UNLOCKABLE,
            DeviceIdleChecker.stateFrom(
                isInteractive = false,
                isKeyguardLocked = false,
                isDeviceSecure = false
            )
        )
    }

    @Test
    fun nonSecureKeyguardIsIdleUnlockable() {
        assertEquals(
            DeviceIdleChecker.DeviceIdleState.IDLE_UNLOCKABLE,
            DeviceIdleChecker.stateFrom(
                isInteractive = true,
                isKeyguardLocked = true,
                isDeviceSecure = false
            )
        )
    }

    @Test
    fun secureKeyguardRequiresAttention() {
        assertEquals(
            DeviceIdleChecker.DeviceIdleState.LOCKED_SECURE,
            DeviceIdleChecker.stateFrom(
                isInteractive = false,
                isKeyguardLocked = true,
                isDeviceSecure = true
            )
        )
    }
}
