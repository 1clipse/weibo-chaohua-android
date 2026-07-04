package com.codex.weibocheckin

import android.content.Context
import android.os.PowerManager

object CheckinWakeLock {
    private var wakeLock: PowerManager.WakeLock? = null

    fun acquire(context: Context, timeoutMs: Long = AppConstants.CHECKIN_WAKELOCK_TIMEOUT_MS) {
        release()
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "${context.packageName}:checkin-window"
        ).apply {
            setReferenceCounted(false)
            acquire(timeoutMs)
        }
    }

    fun release() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }
}
