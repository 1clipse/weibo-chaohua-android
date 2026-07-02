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

        if (!now.isBefore(deadline)) {
            failBecauseNoIdleTime(context)
            return
        }

        if (DeviceIdleChecker.canRunWithoutInterrupting(context)) {
            CheckinScheduler.cancelRetry(context)
            AppPreferences.setTodayStatus(context, CheckinStatus.RUNNING)
            AppPreferences.addLog(context, "检测到锁屏或待机，开始签到")
            val launchIntent = Intent(context, CheckinLaunchActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            CheckinScheduler.scheduleNext(context)
            return
        }

        val nextRetry = IdleRetryCalculator.nextRetryOrNull(now, deadline)
        if (nextRetry == null) {
            failBecauseNoIdleTime(context)
            return
        }

        AppPreferences.setTodayStatus(context, CheckinStatus.WAITING_FOR_IDLE)
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
