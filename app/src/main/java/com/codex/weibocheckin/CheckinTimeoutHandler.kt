package com.codex.weibocheckin

import android.content.Context

object CheckinTimeoutHandler {
    fun handleIfExpired(context: Context, source: String): Boolean {
        if (!AppPreferences.automationActive(context)) return false

        val now = System.currentTimeMillis()
        if (now < AppPreferences.automationDeadline(context)) return false

        val lastStage = AppPreferences.lastStage(context)
        val preview = AppPreferences.lastAccessibilityPreview(context)
        val sawWeiboPage = lastStage.contains(CheckinStage.ROOT_SEEN.label) || preview.isNotBlank()
        val status = if (sawWeiboPage) CheckinStatus.FAILED else CheckinStatus.NEEDS_ATTENTION
        val reason = if (sawWeiboPage) {
            "已打开微博但没有找到签到入口或成功状态"
        } else {
            "打开微博后没有识别到页面，可能被锁屏、后台启动限制或系统省电拦截"
        }
        val log = if (sawWeiboPage) {
            "超时: $source 已读取微博页面但未找到签到入口或成功状态，页面: ${preview.ifBlank { "无文本" }}"
        } else {
            "超时: $source 未收到有效页面事件，可能被系统拦截"
        }

        AppPreferences.stopAutomation(context)
        AppPreferences.setLastStage(context, CheckinStage.BLOCKED, source)
        AppPreferences.setTodayStatus(context, status, reason)
        AppPreferences.addLog(context, log)
        CheckinScheduler.cancelRetry(context)
        CheckinScheduler.cancelWatchdog(context)
        AppPreferences.clearDeferredCheckinState(context)
        CheckinScheduler.scheduleNext(context)
        NotificationHelper.notifyOpenCheckin(
            context,
            "微博签到需要继续",
            "$reason。点按通知会重新预检查，确认可执行后再继续。"
        )
        return true
    }
}
