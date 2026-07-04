package com.codex.weibocheckin

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager

object DeviceIdleChecker {
    enum class DeviceIdleState {
        ACTIVE,
        IDLE_UNLOCKABLE,
        LOCKED_SECURE
    }

    fun currentState(context: Context): DeviceIdleState {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        return stateFrom(
            isInteractive = powerManager?.isInteractive ?: true,
            isKeyguardLocked = keyguardManager?.isKeyguardLocked ?: false,
            isDeviceSecure = keyguardManager?.isDeviceSecure ?: true
        )
    }

    fun stateFrom(
        isInteractive: Boolean,
        isKeyguardLocked: Boolean,
        isDeviceSecure: Boolean
    ): DeviceIdleState {
        if (isKeyguardLocked && isDeviceSecure) {
            return DeviceIdleState.LOCKED_SECURE
        }
        if (!isInteractive || isKeyguardLocked) {
            return DeviceIdleState.IDLE_UNLOCKABLE
        }
        return DeviceIdleState.ACTIVE
    }

    fun canRunWithoutInterrupting(context: Context): Boolean {
        return currentState(context) == DeviceIdleState.IDLE_UNLOCKABLE
    }
}
