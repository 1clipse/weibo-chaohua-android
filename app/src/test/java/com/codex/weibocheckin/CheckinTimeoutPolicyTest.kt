package com.codex.weibocheckin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckinTimeoutPolicyTest {
    @Test
    fun timeoutWithoutPageNeedsAttention() {
        val result = CheckinTimeoutPolicy.classify(
            lastStage = CheckinStage.WEIBO_INTENT_SENT.label,
            preview = "",
            source = "测试 watchdog"
        )

        assertEquals(CheckinStatus.NEEDS_ATTENTION, result.status)
        assertEquals("打开微博后没有识别到页面，可能被锁屏、后台启动限制或系统省电拦截", result.reason)
        assertTrue(result.log.contains("未收到有效页面事件"))
    }

    @Test
    fun timeoutAfterSeeingRootFailsAsMissingEntry() {
        val result = CheckinTimeoutPolicy.classify(
            lastStage = CheckinStage.ROOT_SEEN.label,
            preview = "",
            source = "测试 watchdog"
        )

        assertEquals(CheckinStatus.FAILED, result.status)
        assertEquals("已打开微博但没有找到签到入口或成功状态", result.reason)
        assertTrue(result.log.contains("无文本"))
    }

    @Test
    fun timeoutWithPreviewFailsAsMissingEntryAndKeepsPreview() {
        val result = CheckinTimeoutPolicy.classify(
            lastStage = CheckinStage.WEIBO_INTENT_SENT.label,
            preview = "超话 / 热门",
            source = "测试 watchdog"
        )

        assertEquals(CheckinStatus.FAILED, result.status)
        assertTrue(result.log.contains("超话 / 热门"))
    }
}
