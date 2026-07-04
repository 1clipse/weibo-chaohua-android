package com.codex.weibocheckin

import android.content.Context
import java.time.Instant
import java.time.LocalDateTime

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
    private const val KEY_IDLE_BLOCKER_REASON = "idle_blocker_reason"
    private const val KEY_LAST_STAGE = "last_stage"
    private const val KEY_LAST_STAGE_AT = "last_stage_at"
    private const val KEY_LAST_ACCESSIBILITY_PREVIEW = "last_accessibility_preview"
    private const val KEY_TEMPORARY_TEST_AT = "temporary_test_at"
    private const val KEY_NEXT_DAILY_SCHEDULED_AT = "next_daily_scheduled_at"

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
        normalizedTime(context).first

    fun minute(context: Context): Int =
        normalizedTime(context).second

    fun setTime(context: Context, hour: Int, minute: Int) {
        val (safeHour, safeMinute) = ScheduleTimePolicy.normalized(hour, minute)
        prefs(context).edit().putInt(KEY_HOUR, safeHour).putInt(KEY_MINUTE, safeMinute).apply()
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

    fun setIdleBlockerReason(context: Context, value: String?) {
        prefs(context).edit().putString(KEY_IDLE_BLOCKER_REASON, value.orEmpty()).apply()
    }

    fun idleBlockerReason(context: Context): String =
        prefs(context).getString(KEY_IDLE_BLOCKER_REASON, "").orEmpty()

    fun setTemporaryTestAt(context: Context, value: String?) {
        prefs(context).edit().putString(KEY_TEMPORARY_TEST_AT, value.orEmpty()).apply()
    }

    fun temporaryTestAt(context: Context): String =
        prefs(context).getString(KEY_TEMPORARY_TEST_AT, "").orEmpty()

    fun setNextDailyScheduledAt(context: Context, value: String?) {
        prefs(context).edit().putString(KEY_NEXT_DAILY_SCHEDULED_AT, value.orEmpty()).apply()
    }

    fun nextDailyScheduledAt(context: Context): String =
        prefs(context).getString(KEY_NEXT_DAILY_SCHEDULED_AT, "").orEmpty()

    fun clearDeferredCheckinState(context: Context, clearTemporaryTest: Boolean = true) {
        val editor = prefs(context).edit()
            .remove(KEY_NEXT_RETRY)
            .remove(KEY_IDLE_DEADLINE)
            .remove(KEY_IDLE_BLOCKER_REASON)
        if (clearTemporaryTest) {
            editor.remove(KEY_TEMPORARY_TEST_AT)
        }
        editor.apply()
    }

    fun startAutomation(context: Context, timeoutMs: Long = AppConstants.CHECKIN_TIMEOUT_MS) {
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putLong(KEY_DEADLINE, System.currentTimeMillis() + timeoutMs)
            .putString(KEY_FAILURE_REASON, "")
            .remove(KEY_LAST_ACCESSIBILITY_PREVIEW)
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

    fun setLastStage(context: Context, stage: CheckinStage, detail: String = "") {
        val value = if (detail.isBlank()) stage.label else "${stage.label}: $detail"
        prefs(context).edit()
            .putString(KEY_LAST_STAGE, value)
            .putLong(KEY_LAST_STAGE_AT, System.currentTimeMillis())
            .apply()
    }

    fun lastStage(context: Context): String =
        prefs(context).getString(KEY_LAST_STAGE, "").orEmpty()

    fun lastStageAt(context: Context): Long =
        prefs(context).getLong(KEY_LAST_STAGE_AT, 0L)

    fun setLastAccessibilityPreview(context: Context, value: String) {
        prefs(context).edit().putString(KEY_LAST_ACCESSIBILITY_PREVIEW, value.take(180)).apply()
    }

    fun lastAccessibilityPreview(context: Context): String =
        prefs(context).getString(KEY_LAST_ACCESSIBILITY_PREVIEW, "").orEmpty()

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

    fun clearLogs(context: Context) {
        prefs(context).edit().remove(KEY_LOGS).apply()
    }

    fun clearDiagnostics(context: Context, now: LocalDateTime = LocalDateTime.now()): Boolean {
        if (!DiagnosticResetPolicy.canReset(automationActive(context))) return false
        val keepDeferredState = DiagnosticResetPolicy.shouldKeepDeferredState(
            todayStatus = todayStatus(context),
            nextRetry = nextRetry(context),
            idleDeadline = idleDeadline(context),
            now = now
        )
        val editor = prefs(context).edit()
            .remove(KEY_LOGS)
            .remove(KEY_LAST_ATTEMPT)
            .remove(KEY_FAILURE_REASON)
            .remove(KEY_LAST_STAGE)
            .remove(KEY_LAST_STAGE_AT)
            .remove(KEY_LAST_ACCESSIBILITY_PREVIEW)
        if (keepDeferredState) {
            editor.putString(KEY_FAILURE_REASON, "")
        } else {
            editor
                .remove(KEY_NEXT_RETRY)
                .remove(KEY_IDLE_DEADLINE)
                .remove(KEY_IDLE_BLOCKER_REASON)
                .remove(KEY_TEMPORARY_TEST_AT)
                .remove(KEY_NEXT_DAILY_SCHEDULED_AT)
                .putString(KEY_TODAY_STATUS, CheckinStatus.NOT_RUN.name)
        }
        editor.apply()
        return true
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private fun normalizedTime(context: Context): Pair<Int, Int> {
        val storedHour = prefs(context).getInt(KEY_HOUR, AppConstants.DEFAULT_HOUR)
        val storedMinute = prefs(context).getInt(KEY_MINUTE, AppConstants.DEFAULT_MINUTE)
        return ScheduleTimePolicy.normalized(storedHour, storedMinute)
    }
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

enum class CheckinStage(val label: String) {
    ALARM_FIRED("闹钟已触发"),
    PREFLIGHT_WAITING("预检查等待"),
    LAUNCH_ACTIVITY_STARTED("启动中转页"),
    LAUNCH_ACTIVITY_VISIBLE("中转页已打开"),
    WEIBO_INTENT_SENT("已请求打开微博"),
    ACCESSIBILITY_EVENT_RECEIVED("收到微博无障碍事件"),
    ROOT_SEEN("已读取微博页面"),
    CLICK_SENT("已点击签到"),
    FINISHED("流程已结束"),
    BLOCKED("流程被阻断")
}
