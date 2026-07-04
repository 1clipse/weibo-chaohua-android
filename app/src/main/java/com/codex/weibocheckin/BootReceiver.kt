package com.codex.weibocheckin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        CheckinTimeoutHandler.handleIfExpired(context, "系统事件恢复检查")
        if (AppPreferences.isEnabled(context)) {
            AppPreferences.addLog(context, "系统事件后恢复每日签到: ${intent?.action}")
            CheckinScheduler.scheduleNext(context)
        }
    }
}
