package com.codex.weibocheckin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDateTime

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        CheckinTimeoutHandler.handleIfExpired(context, "系统事件恢复检查")
        if (AppPreferences.isEnabled(context)) {
            val now = LocalDateTime.now()
            AppPreferences.addLog(context, "系统事件后恢复每日签到: ${intent?.action}")
            if (!CheckinScheduler.scheduleNext(context)) {
                AppPreferences.addLog(context, "系统事件后未能恢复每日签到，跳过等待空闲和临时测试恢复")
                return
            }
            val retryAt = RecoveryRetryPolicy.retryAtOrNull(
                todayStatus = AppPreferences.todayStatus(context),
                nextRetry = AppPreferences.nextRetry(context),
                idleDeadline = AppPreferences.idleDeadline(context),
                now = now
            )
            if (retryAt != null) {
                if (CheckinScheduler.scheduleRetry(context, retryAt)) {
                    AppPreferences.addLog(context, "系统事件后恢复等待空闲重试: $retryAt")
                } else {
                    AppPreferences.addLog(context, "系统事件后未能恢复等待空闲重试")
                }
            }
            val temporaryTestAt = RecoveryRetryPolicy.temporaryTestAtOrNull(
                temporaryTestAt = AppPreferences.temporaryTestAt(context),
                now = now
            )
            if (temporaryTestAt != null) {
                if (CheckinScheduler.scheduleTemporaryTestAt(context, temporaryTestAt)) {
                    AppPreferences.addLog(context, "系统事件后恢复临时定时测试: $temporaryTestAt")
                } else {
                    AppPreferences.addLog(context, "系统事件后未能恢复临时定时测试")
                }
            } else if (AppPreferences.temporaryTestAt(context).isNotBlank()) {
                CheckinScheduler.cancelTemporaryTest(context)
                AppPreferences.addLog(context, "系统事件后清理过期临时定时测试")
            }
        }
    }
}
