package com.codex.weibocheckin

import android.content.Context
import android.os.Handler
import android.os.Looper

object InProcessWatchdog {
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null

    fun arm(context: Context, timeoutMs: Long = AppConstants.CHECKIN_TIMEOUT_MS) {
        cancel()
        val appContext = context.applicationContext
        runnable = Runnable {
            val remaining = AppPreferences.automationDeadline(appContext) - System.currentTimeMillis()
            if (remaining > 0L) {
                arm(appContext, remaining.coerceAtMost(timeoutMs))
            } else {
                CheckinTimeoutHandler.handleIfExpired(appContext, "进程内 watchdog")
            }
        }.also { handler.postDelayed(it, timeoutMs) }
    }

    fun cancel() {
        runnable?.let(handler::removeCallbacks)
        runnable = null
    }
}
