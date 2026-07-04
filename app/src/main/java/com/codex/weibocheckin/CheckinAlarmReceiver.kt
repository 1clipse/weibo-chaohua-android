package com.codex.weibocheckin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDateTime

class CheckinAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        NotificationHelper.ensureChannel(context)
        val now = LocalDateTime.now()
        val action = intent?.action
        val storedDeadline = AppPreferences.idleDeadline(context).toLocalDateTimeOrNull()
        val deadline = CheckinDeadlinePolicy.deadlineFor(now, action, storedDeadline)

        AppPreferences.setLastAttempt(context, now.toString())
        AppPreferences.setIdleDeadline(context, deadline.toString())

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
                AppPreferences.setNextDailyScheduledAt(context, null)
                AppPreferences.setLastStage(context, CheckinStage.ALARM_FIRED)
                AppPreferences.addLog(context, "定时任务触发")
                AppPreferences.setTodayStatus(context, CheckinStatus.WAITING_FOR_IDLE)
                AppPreferences.setIdleBlockerReason(context, null)
            }
            AppConstants.ACTION_TEST_CHECKIN -> {
                AppPreferences.setTemporaryTestAt(context, null)
                AppPreferences.setLastStage(context, CheckinStage.ALARM_FIRED, "临时定时测试")
                AppPreferences.addLog(context, "临时定时测试触发")
                AppPreferences.setTodayStatus(context, CheckinStatus.WAITING_FOR_IDLE)
                AppPreferences.setIdleBlockerReason(context, null)
            }
            AppConstants.ACTION_CONTINUE_CHECKIN -> {
                AppPreferences.setLastStage(context, CheckinStage.ALARM_FIRED, "通知继续")
                AppPreferences.addLog(context, "通知继续触发")
            }
            AppConstants.ACTION_IDLE_SIGNAL_CHECKIN -> {
                AppPreferences.setLastStage(context, CheckinStage.ALARM_FIRED, "息屏后立即重试")
                AppPreferences.addLog(context, "息屏后立即重试触发")
            }
            else -> {
                AppPreferences.setLastStage(context, CheckinStage.ALARM_FIRED, "等待空闲重试")
                AppPreferences.addLog(context, "等待空闲重试触发")
            }
        }

        if (!CheckinDeadlinePolicy.isBeforeDeadline(now, deadline)) {
            failBecauseDeadlineReached(
                context = context,
                blockerReason = AppPreferences.idleBlockerReason(context).ifBlank { "23:00 前未检测到可执行状态" }
            )
            return
        }

        if (!NotificationHelper.canNotify(context)) {
            AppPreferences.setLastStage(context, CheckinStage.BLOCKED, "通知权限未开启")
            requestAttentionOrRetry(
                context = context,
                now = now,
                deadline = deadline,
                reason = "通知权限未开启",
                logMessage = "预检查未通过: 通知权限未开启，未进入 RUNNING，未打开微博",
                notificationTitle = "需要开启通知权限",
                notificationMessage = "微博超话签到遇到异常时需要用通知提醒你处理。请先开启本应用通知权限。",
                canContinueFromNotification = false
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

        val weiboStatus = WeiboAppChecker.currentStatus(context)
        val weiboBlockers = CheckinPrerequisitePolicy.blockers(
            weiboInstalled = weiboStatus.installed,
            weiboCanOpenUrl = weiboStatus.canOpenConfiguredUrl,
            notificationsGranted = true,
            accessibilityEnabled = true
        )
        if (weiboBlockers.isNotEmpty()) {
            val reason = "还需处理: ${weiboBlockers.joinToString("、")}"
            AppPreferences.setLastStage(context, CheckinStage.BLOCKED, reason)
            requestAttentionOrRetry(
                context = context,
                now = now,
                deadline = deadline,
                reason = reason,
                logMessage = "预检查未通过: $reason，未进入 RUNNING，未打开微博",
                notificationTitle = "微博签到未启动",
                notificationMessage = "$reason。处理后可回到 App 手动测试或等待自动重试。",
                canContinueFromNotification = false
            )
            return
        }

        val userRequestedContinue = action == AppConstants.ACTION_CONTINUE_CHECKIN
        when (DeviceIdleChecker.currentState(context)) {
            DeviceIdleChecker.DeviceIdleState.IDLE_UNLOCKABLE -> {
                val needsLockscreenLaunch = !userRequestedContinue && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                if (needsLockscreenLaunch && !NotificationHelper.canUseLockscreenLaunch(context)) {
                    requestAttentionOrRetry(
                        context = context,
                        now = now,
                        deadline = deadline,
                        reason = "锁屏启动权限或通知渠道等级不足",
                        logMessage = "预检查未通过: 锁屏启动不可用，未进入 RUNNING，未打开微博",
                        notificationTitle = "需要允许锁屏启动",
                        notificationMessage = "手机空闲时需要全屏通知拉起签到中转页。请在本应用权限状态里打开锁屏启动设置，并保持通知渠道为高优先级。",
                        canContinueFromNotification = false
                    )
                } else {
                    startCheckin(context, useLockscreenLaunch = needsLockscreenLaunch)
                }
            }
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
            DeviceIdleChecker.DeviceIdleState.ACTIVE -> {
                if (userRequestedContinue) {
                    AppPreferences.addLog(context, "用户点按通知继续，允许在当前亮屏状态下执行")
                    startCheckin(context, useLockscreenLaunch = false)
                } else if (action == AppConstants.ACTION_IDLE_SIGNAL_CHECKIN) {
                    AppPreferences.setLastStage(context, CheckinStage.PREFLIGHT_WAITING, "息屏后复检仍在使用")
                    AppPreferences.setTodayStatus(context, CheckinStatus.WAITING_FOR_IDLE)
                    AppPreferences.addLog(context, "息屏后复检时手机仍在使用，保留原有等待空闲重试")
                } else {
                    waitForIdleOrFail(context, now, deadline)
                }
            }
        }
    }

    private fun startCheckin(context: Context, useLockscreenLaunch: Boolean) {
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
        val launchRequested = if (useLockscreenLaunch && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            NotificationHelper.notifyLaunchCheckin(
                context,
                "准备自动签到",
                "检测到手机空闲，正在请求系统从锁屏/息屏状态打开微博签到。"
            ).also { sent ->
                if (sent) AppPreferences.addLog(context, "已发送锁屏全屏通知，请求系统拉起签到中转页")
            }
        } else {
            runCatching {
                context.startActivity(launchIntent)
            }.onSuccess {
                AppPreferences.addLog(context, "已请求系统打开签到中转页")
            }.isSuccess
        }
        if (!launchRequested) {
            CheckinScheduler.cancelWatchdog(context)
            AppPreferences.stopAutomation(context)
            AppPreferences.setLastStage(context, CheckinStage.BLOCKED, "中转页启动失败")
            AppPreferences.setTodayStatus(context, CheckinStatus.FAILED, "系统阻止启动签到流程")
            AppPreferences.addLog(context, "启动签到流程失败: 系统未接受启动请求")
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
        if (!CheckinScheduler.scheduleRetry(context, nextRetry)) {
            failBecauseRescheduleUnavailable(context, "精确闹钟权限未开启，无法继续等待手机空闲")
        }
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
        val alreadyNotifiedForReason =
            AppPreferences.todayStatus(context) == CheckinStatus.NEEDS_ATTENTION.name &&
                AppPreferences.failureReason(context) == reason
        AppPreferences.setLastStage(context, CheckinStage.BLOCKED, reason)
        AppPreferences.setTodayStatus(context, CheckinStatus.NEEDS_ATTENTION, reason)
        AppPreferences.addLog(context, logMessage)
        AppPreferences.addLog(context, "将在 $nextRetry 重新进行签到预检查")
        val retryScheduled = CheckinScheduler.scheduleRetry(context, nextRetry)
        if (!retryScheduled) {
            failBecauseRescheduleUnavailable(context, "精确闹钟权限未开启，无法安排后续重试")
            return
        }
        if (alreadyNotifiedForReason) {
            AppPreferences.addLog(context, "仍需人工处理: $reason")
        } else if (canContinueFromNotification) {
            NotificationHelper.notifyOpenCheckin(context, notificationTitle, notificationMessage)
        } else {
            NotificationHelper.notify(context, notificationTitle, notificationMessage)
        }
    }

    private fun failBecauseDeadlineReached(context: Context, blockerReason: String) {
        CheckinScheduler.cancelRetry(context)
        AppPreferences.clearDeferredCheckinState(context)
        AppPreferences.setLastStage(context, CheckinStage.FINISHED, blockerReason)
        AppPreferences.setTodayStatus(context, CheckinStatus.FAILED, blockerReason)
        AppPreferences.addLog(context, "今日未自动签到: $blockerReason")
        CheckinScheduler.scheduleNext(context)
        NotificationHelper.notify(context, "今日未自动签到", "$blockerReason。请手动处理微博超话签到。")
    }

    private fun failBecauseRescheduleUnavailable(context: Context, reason: String) {
        CheckinScheduler.cancelRetry(context)
        AppPreferences.clearDeferredCheckinState(context)
        AppPreferences.setLastStage(context, CheckinStage.BLOCKED, reason)
        AppPreferences.setTodayStatus(context, CheckinStatus.NEEDS_ATTENTION, reason)
        AppPreferences.addLog(context, "自动重试已停止: $reason")
        CheckinScheduler.scheduleNext(context)
        NotificationHelper.notify(context, "微博签到需要处理", "$reason。请开启精确闹钟权限后重新启用每日签到。")
    }

    private fun deadlineReasonFor(reason: String): String =
        when {
            reason.contains("无障碍") -> "无障碍服务未开启至 23:00 截止时间"
            reason.contains("安全锁屏") -> "设备处于安全锁屏至 23:00 截止时间"
            else -> reason
        }
}

private fun String.toLocalDateTimeOrNull(): LocalDateTime? =
    takeIf { it.isNotBlank() }?.let { value ->
        runCatching { LocalDateTime.parse(value) }.getOrNull()
    }
