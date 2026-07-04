package com.codex.weibocheckin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckinPrerequisitePolicyTest {
    @Test
    fun noBlockersWhenEverythingIsReady() {
        assertTrue(
            CheckinPrerequisitePolicy.blockers(
                weiboInstalled = true,
                weiboCanOpenUrl = true,
                notificationsGranted = true,
                accessibilityEnabled = true
            ).isEmpty()
        )
    }

    @Test
    fun explainsMissingRequirementsInUserFacingOrder() {
        assertEquals(
            listOf("安装微博 App", "开启通知", "开启无障碍"),
            CheckinPrerequisitePolicy.blockers(
                weiboInstalled = false,
                weiboCanOpenUrl = false,
                notificationsGranted = false,
                accessibilityEnabled = false
            )
        )
        assertEquals(
            listOf("检查超话 URL"),
            CheckinPrerequisitePolicy.blockers(
                weiboInstalled = true,
                weiboCanOpenUrl = false,
                notificationsGranted = true,
                accessibilityEnabled = true
            )
        )
    }
}
