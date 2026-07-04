package com.codex.weibocheckin

import android.content.Context
import android.os.Build
import android.os.PowerManager

object BatteryOptimizationChecker {
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
}
