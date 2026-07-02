package com.codex.weibocheckin

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager

object DeviceIdleChecker {
    fun canRunWithoutInterrupting(context: Context): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        return !powerManager.isInteractive || keyguardManager.isKeyguardLocked
    }
}
