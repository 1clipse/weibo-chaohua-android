package com.codex.weibocheckin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CheckinWatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != AppConstants.ACTION_WATCHDOG_CHECKIN) return
        if (!AppPreferences.automationActive(context)) return
        if (System.currentTimeMillis() <= AppPreferences.automationDeadline(context)) return

        AppPreferences.stopAutomation(context)
        AppPreferences.setTodayStatus(
            context,
            CheckinStatus.NEEDS_ATTENTION,
            "打开微博后没有识别到页面，可能被锁屏、后台启动限制或系统省电拦截"
        )
        AppPreferences.addLog(context, "超时: 打开微博后未收到有效页面事件，可能被系统拦截")
        NotificationHelper.notifyOpenCheckin(
            context,
            "微博签到需要继续",
            "没有识别到微博页面。点按通知会重新预检查，确认可执行后再继续。"
        )
        CheckinScheduler.cancelRetry(context)
        CheckinScheduler.scheduleNext(context)
    }
}
