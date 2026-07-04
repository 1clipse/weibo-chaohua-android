package com.codex.weibocheckin

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object NotificationHelper {
    private const val CHANNEL_ID = "weibo_checkin_status_v2"
    private const val LAUNCH_CHECKIN_NOTIFICATION_ID = 1001
    private const val OPEN_CHECKIN_NOTIFICATION_ID = 1002
    private const val LAUNCH_CHECKIN_REQUEST_CODE = 2001
    private const val OPEN_CHECKIN_REQUEST_CODE = 2002

    fun ensureChannel(context: Context): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java)
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "微博签到结果",
                    NotificationManager.IMPORTANCE_HIGH
                )
                manager.createNotificationChannel(channel)
            }
        }.onFailure {
            AppPreferences.addLog(context, "通知渠道初始化失败: ${it.message}")
        }.isSuccess

    fun canNotify(context: Context): Boolean =
        runCatching {
            if (!ensureChannel(context)) return@runCatching false
            val runtimePermissionGranted = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            val appNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            val channelEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java)
                val channel = manager.getNotificationChannel(CHANNEL_ID)
                channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
            } else {
                true
            }
            runtimePermissionGranted && appNotificationsEnabled && channelEnabled
        }.getOrDefault(false)

    fun canUseLockscreenLaunch(context: Context): Boolean =
        runCatching {
            if (!canNotify(context)) return@runCatching false
            val fullScreenIntentAllowed = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                true
            } else {
                context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
            }
            fullScreenIntentAllowed && isHighImportanceChannel(context)
        }.getOrDefault(false)

    fun notify(context: Context, title: String, message: String): Boolean {
        if (!canNotify(context)) {
            AppPreferences.addLog(context, "通知权限未开启: $title - $message")
            return false
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context)
                .notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
        }.onFailure {
            AppPreferences.addLog(context, "通知发送失败: $title - ${it.message}")
        }.isSuccess
    }

    fun notifyOpenCheckin(context: Context, title: String, message: String): Boolean {
        if (!canNotify(context)) {
            AppPreferences.addLog(context, "通知权限未开启: $title - $message")
            return false
        }

        val launchIntent = Intent(context, CheckinContinueActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getActivity(
            context,
            OPEN_CHECKIN_REQUEST_CODE,
            launchIntent,
            pendingIntentFlags
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context)
                .notify(OPEN_CHECKIN_NOTIFICATION_ID, notification)
        }.onFailure {
            AppPreferences.addLog(context, "通知发送失败: $title - ${it.message}")
        }.isSuccess
    }

    fun notifyLaunchCheckin(context: Context, title: String, message: String): Boolean {
        if (!canNotify(context)) {
            AppPreferences.addLog(context, "通知权限未开启: $title - $message")
            return false
        }
        if (!canUseLockscreenLaunch(context)) {
            AppPreferences.addLog(context, "锁屏启动不可用: 全屏通知权限或通知渠道等级不足")
            return false
        }

        val launchIntent = Intent(context, CheckinLaunchActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getActivity(
            context,
            LAUNCH_CHECKIN_REQUEST_CODE,
            launchIntent,
            pendingIntentFlags
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context)
                .notify(LAUNCH_CHECKIN_NOTIFICATION_ID, notification)
        }.onFailure {
            AppPreferences.addLog(context, "锁屏启动通知发送失败: $title - ${it.message}")
        }.isSuccess
    }

    private fun isHighImportanceChannel(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(CHANNEL_ID)
        return channel == null || channel.importance >= NotificationManager.IMPORTANCE_HIGH
    }
}
