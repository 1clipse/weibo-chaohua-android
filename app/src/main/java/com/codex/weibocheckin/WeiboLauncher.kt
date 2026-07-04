package com.codex.weibocheckin

import android.content.Context
import android.content.Intent
import android.net.Uri

object WeiboLauncher {
    fun startCheckin(context: Context) {
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
            AppPreferences.setLastStage(context, CheckinStage.BLOCKED, "微博启动失败")
            AppPreferences.setTodayStatus(context, CheckinStatus.FAILED, "无法打开微博 App")
            AppPreferences.addLog(context, "打开微博失败: ${it.message}")
            NotificationHelper.notify(context, "微博签到启动失败", "无法打开微博 App，请确认已安装微博。")
        }
    }
}
