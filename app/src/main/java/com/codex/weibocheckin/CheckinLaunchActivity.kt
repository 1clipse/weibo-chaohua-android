package com.codex.weibocheckin

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import java.time.LocalDateTime

class CheckinLaunchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppPreferences.setLastStage(this, CheckinStage.LAUNCH_ACTIVITY_VISIBLE)
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        if (DeviceIdleChecker.currentState(this) == DeviceIdleChecker.DeviceIdleState.LOCKED_SECURE) {
            stopForKeyguard("安全锁屏仍未解除")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            if (keyguardManager.isKeyguardLocked) {
                keyguardManager.requestDismissKeyguard(
                    this,
                    object : KeyguardManager.KeyguardDismissCallback() {
                        override fun onDismissSucceeded() = startWeiboAndFinish()
                        override fun onDismissCancelled() = stopForKeyguard("锁屏解除被取消")
                        override fun onDismissError() = stopForKeyguard("系统拒绝解除锁屏")
                    }
                )
                return
            }
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        startWeiboAndFinish()
    }

    private fun startWeiboAndFinish() {
        WeiboLauncher.startCheckin(this)
        finish()
    }

    private fun stopForKeyguard(reason: String) {
        val now = LocalDateTime.now()
        val deadline = AppPreferences.idleDeadline(this).toLocalDateTimeOrNull()
            ?: CheckinDeadlinePolicy.dailyDeadlineFor(now)
        val nextRetry = IdleRetryCalculator.nextRetryOrNull(now, deadline)
        AppPreferences.stopAutomation(this)
        CheckinScheduler.cancelWatchdog(this)
        AppPreferences.setLastStage(this, CheckinStage.BLOCKED, reason)
        AppPreferences.setTodayStatus(this, CheckinStatus.NEEDS_ATTENTION, reason)
        AppPreferences.addLog(this, "启动前复检失败: $reason")
        if (nextRetry != null) {
            AppPreferences.setIdleBlockerReason(this, "设备处于安全锁屏至 23:00 截止时间")
            if (CheckinScheduler.scheduleRetry(this, nextRetry)) {
                AppPreferences.addLog(this, "启动前复检失败后已延后到 $nextRetry 重试")
                NotificationHelper.notifyOpenCheckin(this, "需要解锁后继续签到", "手机仍处于锁屏状态，解锁后点按通知继续。")
            } else {
                AppPreferences.clearDeferredCheckinState(this)
                AppPreferences.setLastStage(this, CheckinStage.BLOCKED, "无法安排安全锁屏重试")
                AppPreferences.setTodayStatus(this, CheckinStatus.NEEDS_ATTENTION, "精确闹钟权限未开启，无法安排安全锁屏重试")
                AppPreferences.addLog(this, "启动前复检失败后无法安排重试: 精确闹钟权限未开启")
                CheckinScheduler.scheduleNext(this)
                NotificationHelper.notify(this, "微博签到需要处理", "精确闹钟权限未开启，无法安排安全锁屏重试。")
            }
        } else {
            AppPreferences.clearDeferredCheckinState(this)
            AppPreferences.setLastStage(this, CheckinStage.FINISHED, "安全锁屏至 23:00 截止时间")
            AppPreferences.setTodayStatus(this, CheckinStatus.FAILED, "设备处于安全锁屏至 23:00 截止时间")
            AppPreferences.addLog(this, "今日未自动签到: 设备处于安全锁屏至 23:00 截止时间")
            CheckinScheduler.scheduleNext(this)
            NotificationHelper.notify(this, "今日未自动签到", "设备处于安全锁屏至 23:00 截止时间。请手动处理微博超话签到。")
        }
        finish()
    }
}

private fun String.toLocalDateTimeOrNull(): LocalDateTime? =
    takeIf { it.isNotBlank() }?.let { value ->
        runCatching { LocalDateTime.parse(value) }.getOrNull()
    }
