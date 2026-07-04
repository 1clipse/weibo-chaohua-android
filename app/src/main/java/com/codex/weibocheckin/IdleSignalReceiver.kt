package com.codex.weibocheckin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDateTime

class IdleSignalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_SCREEN_OFF) return
        val now = LocalDateTime.now()
        val deadline = AppPreferences.idleDeadline(context).toLocalDateTimeOrNull()
        val shouldSendRetryBroadcast = IdleSignalDecision.shouldSendRetryBroadcast(
            enabled = AppPreferences.isEnabled(context),
            automationActive = AppPreferences.automationActive(context),
            todayStatus = AppPreferences.todayStatus(context),
            now = now,
            idleDeadline = deadline,
            deviceState = DeviceIdleChecker.currentState(context)
        )

        if (!shouldSendRetryBroadcast) return

        val retryAt = now.plusSeconds(AppConstants.SCREEN_OFF_RECHECK_DELAY_SECONDS)
        AppPreferences.addLog(context, "检测到息屏，准备短延迟重新检查是否可以签到")
        CheckinScheduler.scheduleIdleSignalCheck(context, retryAt)
    }
}

private fun String.toLocalDateTimeOrNull(): LocalDateTime? =
    takeIf { it.isNotBlank() }?.let { value ->
        runCatching { LocalDateTime.parse(value) }.getOrNull()
    }
