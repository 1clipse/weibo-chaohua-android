package com.codex.weibocheckin

import android.content.Context
import java.time.Instant

object AppPreferences {
    private const val NAME = "weibo_checkin_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_URL = "chaohua_url"
    private const val KEY_HOUR = "hour"
    private const val KEY_MINUTE = "minute"
    private const val KEY_ACTIVE = "automation_active"
    private const val KEY_DEADLINE = "automation_deadline"
    private const val KEY_LOGS = "logs"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_TODAY_STATUS = "today_status"
    private const val KEY_LAST_ATTEMPT = "last_attempt"
    private const val KEY_NEXT_RETRY = "next_retry"
    private const val KEY_IDLE_DEADLINE = "idle_deadline"
    private const val KEY_FAILURE_REASON = "failure_reason"

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun chaohuaUrl(context: Context): String =
        prefs(context).getString(KEY_URL, AppConstants.DEFAULT_CHAOHUA_URL) ?: AppConstants.DEFAULT_CHAOHUA_URL

    fun setChaohuaUrl(context: Context, url: String) {
        prefs(context).edit().putString(KEY_URL, url).apply()
    }

    fun hour(context: Context): Int =
        prefs(context).getInt(KEY_HOUR, AppConstants.DEFAULT_HOUR)

    fun minute(context: Context): Int =
        prefs(context).getInt(KEY_MINUTE, AppConstants.DEFAULT_MINUTE)

    fun setTime(context: Context, hour: Int, minute: Int) {
        prefs(context).edit().putInt(KEY_HOUR, hour).putInt(KEY_MINUTE, minute).apply()
    }

    fun darkMode(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DARK_MODE, false)

    fun setDarkMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }

    fun todayStatus(context: Context): String =
        prefs(context).getString(KEY_TODAY_STATUS, CheckinStatus.NOT_RUN.name) ?: CheckinStatus.NOT_RUN.name

    fun setTodayStatus(context: Context, status: CheckinStatus, reason: String? = null) {
        prefs(context).edit()
            .putString(KEY_TODAY_STATUS, status.name)
            .putString(KEY_FAILURE_REASON, reason.orEmpty())
            .apply()
    }

    fun failureReason(context: Context): String =
        prefs(context).getString(KEY_FAILURE_REASON, "").orEmpty()

    fun setLastAttempt(context: Context, value: String) {
        prefs(context).edit().putString(KEY_LAST_ATTEMPT, value).apply()
    }

    fun lastAttempt(context: Context): String =
        prefs(context).getString(KEY_LAST_ATTEMPT, "").orEmpty()

    fun setNextRetry(context: Context, value: String?) {
        prefs(context).edit().putString(KEY_NEXT_RETRY, value.orEmpty()).apply()
    }

    fun nextRetry(context: Context): String =
        prefs(context).getString(KEY_NEXT_RETRY, "").orEmpty()

    fun setIdleDeadline(context: Context, value: String?) {
        prefs(context).edit().putString(KEY_IDLE_DEADLINE, value.orEmpty()).apply()
    }

    fun idleDeadline(context: Context): String =
        prefs(context).getString(KEY_IDLE_DEADLINE, "").orEmpty()

    fun startAutomation(context: Context, timeoutMs: Long = AppConstants.CHECKIN_TIMEOUT_MS) {
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putLong(KEY_DEADLINE, System.currentTimeMillis() + timeoutMs)
            .apply()
    }

    fun stopAutomation(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, false)
            .remove(KEY_DEADLINE)
            .apply()
    }

    fun automationActive(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ACTIVE, false)

    fun automationDeadline(context: Context): Long =
        prefs(context).getLong(KEY_DEADLINE, 0L)

    fun addLog(context: Context, message: String) {
        val line = "${Instant.now()}  $message"
        val current = logs(context).toMutableList()
        current.add(0, line)
        prefs(context).edit().putString(KEY_LOGS, current.take(50).joinToString("\n")).apply()
    }

    fun logs(context: Context): List<String> =
        prefs(context).getString(KEY_LOGS, "").orEmpty()
            .lineSequence()
            .filter { it.isNotBlank() }
            .toList()

    private fun prefs(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}

enum class CheckinStatus {
    NOT_RUN,
    WAITING_FOR_IDLE,
    RUNNING,
    SUCCESS,
    ALREADY_DONE,
    FAILED,
    NEEDS_ATTENTION
}
