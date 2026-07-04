package com.codex.weibocheckin

import android.content.Context
import android.provider.Settings
import android.text.TextUtils

object AccessibilityStatusChecker {
    fun isServiceEnabled(context: Context): Boolean {
        val expected = "${context.packageName}/${WeiboAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(":").any { TextUtils.equals(it, expected) }
    }
}
