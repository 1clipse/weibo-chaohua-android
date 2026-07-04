package com.codex.weibocheckin

object CheckinPrerequisitePolicy {
    fun blockers(
        weiboInstalled: Boolean,
        weiboCanOpenUrl: Boolean,
        notificationsGranted: Boolean,
        accessibilityEnabled: Boolean
    ): List<String> = buildList {
        if (!weiboInstalled) add("安装微博 App")
        if (weiboInstalled && !weiboCanOpenUrl) add("检查超话 URL")
        if (!notificationsGranted) add("开启通知")
        if (!accessibilityEnabled) add("开启无障碍")
    }
}
