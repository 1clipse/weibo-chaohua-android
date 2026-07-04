package com.codex.weibocheckin

import android.content.Context
import android.content.Intent
import android.net.Uri

object WeiboLauncher {
    fun startCheckin(context: Context): Boolean {
        val weiboStatus = WeiboAppChecker.currentStatus(context)
        val blockers = CheckinPrerequisitePolicy.blockers(
            weiboInstalled = weiboStatus.installed,
            weiboCanOpenUrl = weiboStatus.canOpenConfiguredUrl,
            notificationsGranted = NotificationHelper.canNotify(context),
            accessibilityEnabled = AccessibilityStatusChecker.isServiceEnabled(context)
        )
        if (blockers.isNotEmpty()) {
            val reason = "还需处理: ${blockers.joinToString("、")}"
            AppPreferences.stopAutomation(context)
            CheckinScheduler.cancelWatchdog(context)
            AppPreferences.clearDeferredCheckinState(context)
            AppPreferences.setLastStage(context, CheckinStage.BLOCKED, reason)
            AppPreferences.setTodayStatus(context, CheckinStatus.NEEDS_ATTENTION, reason)
            AppPreferences.addLog(context, "签到未启动: $reason")
            if (NotificationHelper.canNotify(context)) {
                NotificationHelper.notify(context, "微博签到未启动", reason)
            }
            return false
        }

        AppPreferences.setTodayStatus(context, CheckinStatus.RUNNING)
        AppPreferences.setNextRetry(context, null)
        AppPreferences.startAutomation(context)
        CheckinScheduler.scheduleWatchdog(context)
        CheckinWakeLock.acquire(context)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AppPreferences.chaohuaUrl(context))).apply {
            setPackage(AppConstants.WEIBO_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(intent)
            AppPreferences.setLastStage(context, CheckinStage.WEIBO_INTENT_SENT)
            AppPreferences.addLog(context, "已请求打开微博超话，等待无障碍服务识别")
        }.onFailure {
            CheckinScheduler.cancelWatchdog(context)
            AppPreferences.stopAutomation(context)
            AppPreferences.clearDeferredCheckinState(context)
            AppPreferences.setLastStage(context, CheckinStage.BLOCKED, "微博启动失败")
            AppPreferences.setTodayStatus(context, CheckinStatus.FAILED, "无法打开微博 App")
            AppPreferences.addLog(context, "打开微博失败: ${it.message}")
            NotificationHelper.notify(context, "微博签到启动失败", "无法打开微博 App，请确认已安装微博。")
            return false
        }
        return true
    }
}
