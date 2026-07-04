package com.codex.weibocheckin

import android.content.Context

object CheckinTimeoutHandler {
    fun handleIfExpired(context: Context, source: String): Boolean {
        if (!AppPreferences.automationActive(context)) return false

        val now = System.currentTimeMillis()
        if (now < AppPreferences.automationDeadline(context)) return false

        AppPreferences.stopAutomation(context)
        AppPreferences.setLastStage(context, CheckinStage.BLOCKED, source)
        AppPreferences.setTodayStatus(
            context,
            CheckinStatus.NEEDS_ATTENTION,
            "打开微博后没有识别到页面，可能被锁屏、后台启动限制或系统省电拦截"
        )
        AppPreferences.addLog(context, "超时: $source 未收到有效页面事件，可能被系统拦截")
        NotificationHelper.notifyOpenCheckin(
            context,
            "微博签到需要继续",
            "没有识别到微博页面。点按通知会重新预检查，确认可执行后再继续。"
        )
        CheckinScheduler.cancelRetry(context)
        CheckinScheduler.cancelWatchdog(context)
        CheckinScheduler.scheduleNext(context)
        return true
    }
}
