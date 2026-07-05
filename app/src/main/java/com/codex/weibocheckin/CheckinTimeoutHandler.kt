package com.codex.weibocheckin

import android.content.Context

object CheckinTimeoutHandler {
    fun handleIfExpired(context: Context, source: String): Boolean {
        if (!AppPreferences.automationActive(context)) return false

        val now = System.currentTimeMillis()
        if (now < AppPreferences.automationDeadline(context)) return false

        val lastStage = AppPreferences.lastStage(context)
        val preview = AppPreferences.lastAccessibilityPreview(context)
        val timeoutResult = CheckinTimeoutPolicy.classify(lastStage, preview, source)

        AppPreferences.stopAutomation(context)
        AppPreferences.setLastStage(context, CheckinStage.BLOCKED, source)
        AppPreferences.setTodayStatus(context, timeoutResult.status, timeoutResult.reason)
        AppPreferences.addLog(context, timeoutResult.log)
        CheckinScheduler.cancelRetry(context)
        CheckinScheduler.cancelWatchdog(context)
        AppPreferences.clearDeferredCheckinState(context)
        CheckinScheduler.scheduleNext(context)
        NotificationHelper.notifyOpenCheckin(
            context,
            "微博签到需要继续",
            "${timeoutResult.reason}。点按通知会重新预检查，确认可执行后再继续。"
        )
        return true
    }
}
