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

    fun canScheduleExact(context: Context): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }

    fun scheduleNext(context: Context) {
        if (!AppPreferences.isEnabled(context)) return
        val now = LocalDateTime.now()
        val next = ScheduleCalculator.nextDailyRun(now, AppPreferences.hour(context), AppPreferences.minute(context))
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DAILY_REQUEST_CODE,
            Intent(context, CheckinAlarmReceiver::class.java).setAction(AppConstants.ACTION_START_CHECKIN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (canScheduleExact(context)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent)
        }
        AppPreferences.addLog(context, "下次签到已安排: $next")
    }

    fun scheduleRetry(context: Context, retryAt: LocalDateTime) {
        val millis = retryAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            RETRY_REQUEST_CODE,
            Intent(context, CheckinAlarmReceiver::class.java).setAction(AppConstants.ACTION_RETRY_CHECKIN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (canScheduleExact(context)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent)
        }
        AppPreferences.setNextRetry(context, retryAt.toString())
        AppPreferences.addLog(context, "手机正在使用，已延后到 $retryAt 重试")
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
        AppPreferences.setNextRetry(context, null)
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DAILY_REQUEST_CODE,
            Intent(context, CheckinAlarmReceiver::class.java).setAction(AppConstants.ACTION_START_CHECKIN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        cancelRetry(context)
        AppPreferences.addLog(context, "每日签到已关闭")
    }
}
