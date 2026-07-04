package com.codex.weibocheckin

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

object WeiboAppChecker {
    data class Status(
        val installed: Boolean,
        val versionName: String,
        val canOpenConfiguredUrl: Boolean
    )

    fun currentStatus(context: Context): Status {
        val packageManager = context.packageManager
        val versionName = runCatching {
            packageManager.getPackageInfo(AppConstants.WEIBO_PACKAGE, 0).versionName.orEmpty()
        }.getOrDefault("")
        val installed = versionName.isNotBlank()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AppPreferences.chaohuaUrl(context))).apply {
            setPackage(AppConstants.WEIBO_PACKAGE)
        }
        val canOpen = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
        return Status(
            installed = installed,
            versionName = versionName,
            canOpenConfiguredUrl = canOpen
        )
    }
}
