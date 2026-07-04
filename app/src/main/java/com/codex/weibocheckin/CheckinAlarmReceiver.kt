package com.codex.weibocheckin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDateTime

class CheckinAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        NotificationHelper.ensureChannel(context)
        val now = LocalDateTime.now()
        val deadline = now
            .withHour(AppConstants.IDLE_DEADLINE_HOUR)
            .withMinute(AppConstants.IDLE_DEADLINE_MINUTE)
            .withSecond(0)
            .withNano(0)

        AppPreferences.setLastAttempt(context, now.toString())
        AppPreferences.setIdleDeadline(context, deadline.toString())

        if (intent?.action == AppConstants.ACTION_START_CHECKIN) {
            AppPreferences.addLog(context, "定时任务触发")
            AppPreferences.setTodayStatus(context, CheckinStatus.WAITING_FOR_IDLE)
        } else {
            AppPreferences.addLog(context, "等待空闲重试触发")
        }

        if (!AccessibilityStatusChecker.isServiceEnabled(context)) {
            requestAttentionOrRetry(
                context = context,
                now = now,
                deadline = deadline,
                reason = "无障碍服务未开启",
                logMessage = "预检查未通过: 无障碍服务未开启，未进入 RUNNING，未打开微博",
                notificationTitle = "需要打开无障碍服务",
                notificationMessage = "微博超话签到需要先开启本应用的无障碍服务，请开启后等待自动重试或手动签到。"
            )
            return
        }

        if (!now.isBefore(deadline)) {
            failBecauseNoIdleTime(context)
            return
        }

        when (DeviceIdleChecker.currentState(context)) {
            DeviceIdleChecker.DeviceIdleState.IDLE_UNLOCKABLE -> startCheckin(context)
            DeviceIdleChecker.DeviceIdleState.LOCKED_SECURE -> requestAttentionOrRetry(
                context = context,
                now = now,
                deadline = deadline,
                reason = "设备处于安全锁屏",
                logMessage = "预检查未通过: 设备处于安全锁屏，未进入 RUNNING，未打开微博",
                notificationTitle = "需要解锁后继续签到",
                notificationMessage = "手机处于安全锁屏状态，无法可靠自动打开微博。请解锁后保持空闲，系统会继续重试到 23:00。"
            )
            DeviceIdleChecker.DeviceIdleState.ACTIVE -> waitForIdleOrFail(context, now, deadline)
        }
    }

    private fun startCheckin(context: Context) {
        CheckinScheduler.cancelRetry(context)
        AppPreferences.setTodayStatus(context, CheckinStatus.RUNNING)
        AppPreferences.addLog(context, "检测到待机或非安全锁屏，开始签到")
        val launchIntent = Intent(context, CheckinLaunchActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        CheckinScheduler.scheduleNext(context)
    }

    private fun waitForIdleOrFail(context: Context, now: LocalDateTime, deadline: LocalDateTime) {
        val nextRetry = IdleRetryCalculator.nextRetryOrNull(now, deadline)
        if (nextRetry == null) {
            failBecauseNoIdleTime(context)
            return
        }

        AppPreferences.setTodayStatus(context, CheckinStatus.WAITING_FOR_IDLE)
        CheckinScheduler.scheduleRetry(context, nextRetry)
    }

    private fun requestAttentionOrRetry(
        context: Context,
        now: LocalDateTime,
        deadline: LocalDateTime,
        reason: String,
        logMessage: String,
        notificationTitle: String,
        notificationMessage: String
    ) {
        AppPreferences.setTodayStatus(context, CheckinStatus.NEEDS_ATTENTION, reason)
        AppPreferences.addLog(context, logMessage)
        NotificationHelper.notify(context, notificationTitle, notificationMessage)

        val nextRetry = IdleRetryCalculator.nextRetryOrNull(now, deadline)
        if (nextRetry == null) {
            CheckinScheduler.cancelRetry(context)
            AppPreferences.addLog(context, "已到 23:00 截止时间，不再重试今日自动签到")
            CheckinScheduler.scheduleNext(context)
            return
        }

        AppPreferences.addLog(context, "将在 $nextRetry 重新进行签到预检查")
        CheckinScheduler.scheduleRetry(context, nextRetry)
    }

    private fun failBecauseNoIdleTime(context: Context) {
        CheckinScheduler.cancelRetry(context)
        AppPreferences.setTodayStatus(context, CheckinStatus.FAILED, "23:00 前手机一直处于使用状态")
        AppPreferences.addLog(context, "今日未自动签到: 23:00 前未检测到锁屏或待机")
        NotificationHelper.notify(context, "今日未自动签到", "23:00 前手机一直在使用，请手动处理微博超话签到。")
        CheckinScheduler.scheduleNext(context)
    }
}
