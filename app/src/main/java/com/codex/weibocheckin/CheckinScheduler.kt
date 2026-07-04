package com.codex.weibocheckin

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDateTime
import java.time.ZoneId

object CheckinScheduler {
    private const val DAILY_REQUEST_CODE = 1001
    private const val RETRY_REQUEST_CODE = 1002
    private const val WATCHDOG_REQUEST_CODE = 1003
    private const val TEST_REQUEST_CODE = 1004
    private const val IDLE_SIGNAL_REQUEST_CODE = 1005

    fun canScheduleExact(context: Context): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }

    fun scheduleNext(context: Context): Boolean {
        if (!AppPreferences.isEnabled(context)) return false
        if (!canScheduleExact(context)) {
            AppPreferences.setEnabled(context, false)
            cancelDailySchedule(context)
            cancelRetry(context)
            cancelTemporaryTest(context)
            AppPreferences.setNextDailyScheduledAt(context, null)
            AppPreferences.addLog(context, "每日签到已关闭: 请先开启精确闹钟权限")
            return false
        }
        if (!NotificationHelper.canNotify(context)) {
            AppPreferences.setEnabled(context, false)
            cancelDailySchedule(context)
            cancelRetry(context)
            cancelTemporaryTest(context)
            AppPreferences.setNextDailyScheduledAt(context, null)
            AppPreferences.addLog(context, "每日签到已关闭: 请先开启通知权限")
            return false
        }
        val now = LocalDateTime.now()
        val next = ScheduleCalculator.nextDailyRun(now, AppPreferences.hour(context), AppPreferences.minute(context))
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        AppPreferences.setNextDailyScheduledAt(context, null)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DAILY_REQUEST_CODE,
            Intent(context, CheckinAlarmReceiver::class.java).setAction(AppConstants.ACTION_START_CHECKIN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val scheduled = scheduleExact(
            context = context,
            alarmManager = alarmManager,
            millis = millis,
            pendingIntent = pendingIntent,
            failureLog = "每日签到未安排: 精确闹钟权限被系统拒绝"
        ) {
            AppPreferences.setNextDailyScheduledAt(context, next.toString())
            AppPreferences.addLog(context, "已请求系统在 $next 触发下次签到尝试")
        }
        if (!scheduled) {
            AppPreferences.setEnabled(context, false)
            cancelDailySchedule(context)
            AppPreferences.setNextDailyScheduledAt(context, null)
            cancelRetry(context)
            cancelTemporaryTest(context)
            AppPreferences.addLog(context, "每日签到已关闭: 系统拒绝安排精确闹钟")
        }
        return scheduled
    }

    fun scheduleTemporaryTest(context: Context, delayMinutes: Long = 2L): Boolean {
        val testAt = LocalDateTime.now().plusMinutes(delayMinutes)
        return scheduleTemporaryTestAt(context, testAt)
    }

    fun scheduleTemporaryTestAt(context: Context, testAt: LocalDateTime): Boolean {
        if (!canScheduleExact(context)) {
            AppPreferences.setTemporaryTestAt(context, null)
            AppPreferences.addLog(context, "临时定时测试未安排: 请先开启精确闹钟权限")
            return false
        }
        val millis = testAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        AppPreferences.setTemporaryTestAt(context, null)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            TEST_REQUEST_CODE,
            Intent(context, CheckinAlarmReceiver::class.java).setAction(AppConstants.ACTION_TEST_CHECKIN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return scheduleExact(
            context = context,
            alarmManager = alarmManager,
            millis = millis,
            pendingIntent = pendingIntent,
            failureLog = "临时定时测试未安排: 精确闹钟权限被系统拒绝"
        ) {
            AppPreferences.setTemporaryTestAt(context, testAt.toString())
            AppPreferences.addLog(context, "已安排临时定时测试: $testAt")
        }
    }

    fun scheduleWatchdog(context: Context, timeoutMs: Long = AppConstants.CHECKIN_TIMEOUT_MS) {
        InProcessWatchdog.arm(context, timeoutMs)
        val millis = System.currentTimeMillis() + timeoutMs
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            WATCHDOG_REQUEST_CODE,
            Intent(context, CheckinWatchdogReceiver::class.java).setAction(AppConstants.ACTION_WATCHDOG_CHECKIN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            if (canScheduleExact(context)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent)
            }
        }.onSuccess {
            AppPreferences.addLog(context, "已安排签到超时检查")
        }.onFailure {
            AppPreferences.addLog(context, "系统 watchdog 未安排成功，将依赖进程内 watchdog: ${it.message}")
        }
    }

    fun cancelWatchdog(context: Context) {
        InProcessWatchdog.cancel()
        CheckinWakeLock.release()
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            WATCHDOG_REQUEST_CODE,
            Intent(context, CheckinWatchdogReceiver::class.java).setAction(AppConstants.ACTION_WATCHDOG_CHECKIN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun scheduleRetry(context: Context, retryAt: LocalDateTime): Boolean {
        if (!canScheduleExact(context)) {
            AppPreferences.setNextRetry(context, null)
            AppPreferences.addLog(context, "等待空闲重试未安排: 请先开启精确闹钟权限")
            return false
        }
        val millis = retryAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        AppPreferences.setNextRetry(context, null)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            RETRY_REQUEST_CODE,
            Intent(context, CheckinAlarmReceiver::class.java).setAction(AppConstants.ACTION_RETRY_CHECKIN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return scheduleExact(
            context = context,
            alarmManager = alarmManager,
            millis = millis,
            pendingIntent = pendingIntent,
            failureLog = "等待空闲重试未安排: 精确闹钟权限被系统拒绝"
        ) {
            AppPreferences.setNextRetry(context, retryAt.toString())
            AppPreferences.addLog(context, "手机正在使用，已延后到 $retryAt 重试")
        }
    }

    fun scheduleIdleSignalCheck(context: Context, retryAt: LocalDateTime): Boolean {
        if (!canScheduleExact(context)) {
            AppPreferences.addLog(context, "息屏快重试未安排: 请先开启精确闹钟权限")
            return false
        }
        val millis = retryAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            IDLE_SIGNAL_REQUEST_CODE,
            Intent(context, CheckinAlarmReceiver::class.java).setAction(AppConstants.ACTION_IDLE_SIGNAL_CHECKIN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return scheduleExact(
            context = context,
            alarmManager = alarmManager,
            millis = millis,
            pendingIntent = pendingIntent,
            failureLog = "息屏快重试未安排: 精确闹钟权限被系统拒绝"
        ) {
            AppPreferences.addLog(context, "息屏后将在 $retryAt 重新检查是否可签到")
        }
    }

    fun cancelRetry(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            RETRY_REQUEST_CODE,
            Intent(context, CheckinAlarmReceiver::class.java).setAction(AppConstants.ACTION_RETRY_CHECKIN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        cancelIdleSignalCheck(context)
        AppPreferences.setNextRetry(context, null)
    }

    fun cancelIdleSignalCheck(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            IDLE_SIGNAL_REQUEST_CODE,
            Intent(context, CheckinAlarmReceiver::class.java).setAction(AppConstants.ACTION_IDLE_SIGNAL_CHECKIN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun cancel(context: Context) {
        AppPreferences.stopAutomation(context)
        cancelDailySchedule(context)
        cancelTemporaryTest(context)
        cancelRetry(context)
        cancelWatchdog(context)
        AppPreferences.setNextDailyScheduledAt(context, null)
        AppPreferences.addLog(context, "每日签到已关闭")
    }

    fun cancelDailySchedule(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DAILY_REQUEST_CODE,
            Intent(context, CheckinAlarmReceiver::class.java).setAction(AppConstants.ACTION_START_CHECKIN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        AppPreferences.setNextDailyScheduledAt(context, null)
    }

    fun cancelTemporaryTest(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            TEST_REQUEST_CODE,
            Intent(context, CheckinAlarmReceiver::class.java).setAction(AppConstants.ACTION_TEST_CHECKIN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        AppPreferences.setTemporaryTestAt(context, null)
    }

    private fun scheduleExact(
        context: Context,
        alarmManager: AlarmManager,
        millis: Long,
        pendingIntent: PendingIntent,
        failureLog: String,
        onSuccess: () -> Unit
    ): Boolean =
        runCatching {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent)
        }.fold(
            onSuccess = {
                onSuccess()
                true
            },
            onFailure = {
                AppPreferences.addLog(context, "$failureLog: ${it.message}")
                false
            }
        )
}
