package com.codex.weibocheckin

object AppConstants {
    const val WEIBO_PACKAGE = "com.sina.weibo"
    const val DEFAULT_CHAOHUA_URL =
        "https://weibo.com/p/100808c6b08fe8916e95b4d0cd85a03a66fa0b?k=%E6%9E%97%E4%BF%8A%E6%9D%B0&_from_=huati_thread"
    const val DEFAULT_HOUR = 10
    const val DEFAULT_MINUTE = 0
    const val CHECKIN_TIMEOUT_MS = 30_000L
    const val CLICK_DEBOUNCE_MS = 1_500L
    const val IDLE_RETRY_INTERVAL_MINUTES = 15L
    const val IDLE_DEADLINE_HOUR = 23
    const val IDLE_DEADLINE_MINUTE = 0
    const val ACTION_START_CHECKIN = "com.codex.weibocheckin.START_CHECKIN"
    const val ACTION_RETRY_CHECKIN = "com.codex.weibocheckin.RETRY_CHECKIN"
    const val ACTION_WATCHDOG_CHECKIN = "com.codex.weibocheckin.WATCHDOG_CHECKIN"
}
