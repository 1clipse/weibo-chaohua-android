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
        val action = intent?.action

        if (!AppPreferences.isEnabled(context)) {
            AppPreferences.stopAutomation(context)
            CheckinScheduler.cancelRetry(context)
            CheckinScheduler.cancelWatchdog(context)
            AppPreferences.addLog(context, "签到触发已忽略: 每日签到已关闭")
            NotificationHelper.notify(context, "微博签到未启动", "每日签到开关已关闭。")
            return
        }

        when (action) {
            AppConstants.ACTION_START_CHECKIN -> {
                AppPreferences.setLastStage(context, CheckinStage.ALARM_FIRED)
                AppPreferences.addLog(context, "定时任务触发")
                AppPreferences.setTodayStatus(context, CheckinStatus.WAITING_FOR_IDLE)
                AppPreferences.setIdleBlockerReason(context, null)
            }
            AppConstants.ACTION_CONTINUE_CHECKIN -> {
                AppPreferences.setLastStage(context, CheckinStage.ALARM_FIRED, "通知继续")
                AppPreferences.addLog(context, "通知继续触发")
            }
            else -> {
                AppPreferences.setLastStage(context, CheckinStage.ALARM_FIRED, "等待空闲重试")
                AppPreferences.addLog(context, "等待空闲重试触发")
            }
        }

        val finalAttemptDeadline = deadline.plusMinutes(AppConstants.IDLE_DEADLINE_GRACE_MINUTES)
        val canUseGraceAttempt = action == AppConstants.ACTION_RETRY_CHECKIN && !now.isAfter(finalAttemptDeadline)
        if (!now.isBefore(deadline) && !canUseGraceAttempt) {
            failBecauseDeadlineReached(
                context = context,
                blockerReason = AppPreferences.idleBlockerReason(context).ifBlank { "23:00 前未检测到可执行状态" }
            )
            return
        }

        if (!AccessibilityStatusChecker.isServiceEnabled(context)) {
            AppPreferences.setLastStage(context, CheckinStage.BLOCKED, "无障碍服务未开启")
            requestAttentionOrRetry(
                context = context,
                now = now,
                deadline = deadline,
                reason = "无障碍服务未开启",
                logMessage = "预检查未通过: 无障碍服务未开启，未进入 RUNNING，未打开微博",
                notificationTitle = "需要打开无障碍服务",
                notificationMessage = "微博超话签到需要先开启本应用的无障碍服务。开启后可回到 App 手动测试或等待自动重试。",
                canContinueFromNotification = false
            )
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
                notificationMessage = "手机处于安全锁屏状态，无法可靠自动打开微博。解锁后可点通知继续尝试。",
                canContinueFromNotification = true
            )
            DeviceIdleChecker.DeviceIdleState.ACTIVE -> waitForIdleOrFail(context, now, deadline)
        }
    }

    private fun startCheckin(context: Context) {
        CheckinScheduler.cancelRetry(context)
        AppPreferences.setIdleBlockerReason(context, null)
        AppPreferences.setTodayStatus(context, CheckinStatus.RUNNING)
        AppPreferences.startAutomation(context)
        CheckinScheduler.scheduleWatchdog(context)
        CheckinWakeLock.acquire(context)
        AppPreferences.setLastStage(context, CheckinStage.LAUNCH_ACTIVITY_STARTED)
        AppPreferences.addLog(context, "检测到待机或非安全锁屏，开始签到")
        val launchIntent = Intent(context, CheckinLaunchActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching {
            context.startActivity(launchIntent)
        }.onFailure {
            CheckinScheduler.cancelWatchdog(context)
            AppPreferences.stopAutomation(context)
            AppPreferences.setLastStage(context, CheckinStage.BLOCKED, "中转页启动失败")
            AppPreferences.setTodayStatus(context, CheckinStatus.FAILED, "系统阻止启动签到流程")
            AppPreferences.addLog(context, "启动签到流程失败: ${it.message}")
            NotificationHelper.notifyOpenCheckin(context, "微博签到需要继续", "系统阻止自动打开微博。点按通知可重新预检查后继续。")
        }
        CheckinScheduler.scheduleNext(context)
    }

    private fun waitForIdleOrFail(context: Context, now: LocalDateTime, deadline: LocalDateTime) {
        val nextRetry = IdleRetryCalculator.nextRetryOrNull(now, deadline)
        if (nextRetry == null) {
            failBecauseDeadlineReached(context, "23:00 前手机一直处于使用状态")
            return
        }

        AppPreferences.setIdleBlockerReason(context, "23:00 前手机一直处于使用状态")
        AppPreferences.setLastStage(context, CheckinStage.PREFLIGHT_WAITING, "手机正在使用")
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
        notificationMessage: String,
        canContinueFromNotification: Boolean
    ) {
        val nextRetry = IdleRetryCalculator.nextRetryOrNull(now, deadline)
        if (nextRetry == null) {
            failBecauseDeadlineReached(context, deadlineReasonFor(reason))
            return
        }

        AppPreferences.setIdleBlockerReason(context, deadlineReasonFor(reason))
        AppPreferences.setLastStage(context, CheckinStage.BLOCKED, reason)
        AppPreferences.setTodayStatus(context, CheckinStatus.NEEDS_ATTENTION, reason)
        AppPreferences.addLog(context, logMessage)
        if (canContinueFromNotification) {
            NotificationHelper.notifyOpenCheckin(context, notificationTitle, notificationMessage)
        } else {
            NotificationHelper.notify(context, notificationTitle, notificationMessage)
        }

        AppPreferences.addLog(context, "将在 $nextRetry 重新进行签到预检查")
        CheckinScheduler.scheduleRetry(context, nextRetry)
    }

    private fun failBecauseDeadlineReached(context: Context, blockerReason: String) {
        CheckinScheduler.cancelRetry(context)
        AppPreferences.setIdleBlockerReason(context, null)
        AppPreferences.setLastStage(context, CheckinStage.FINISHED, blockerReason)
        AppPreferences.setTodayStatus(context, CheckinStatus.FAILED, blockerReason)
        AppPreferences.addLog(context, "今日未自动签到: $blockerReason")
        NotificationHelper.notify(context, "今日未自动签到", "$blockerReason。请手动处理微博超话签到。")
        CheckinScheduler.scheduleNext(context)
    }

    private fun deadlineReasonFor(reason: String): String =
        when {
            reason.contains("无障碍") -> "无障碍服务未开启至 23:00 截止时间"
            reason.contains("安全锁屏") -> "设备处于安全锁屏至 23:00 截止时间"
            else -> reason
        }
}
