package com.codex.weibocheckin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CheckinWatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != AppConstants.ACTION_WATCHDOG_CHECKIN) return
        CheckinTimeoutHandler.handleIfExpired(context, "系统 watchdog")
    }
}
