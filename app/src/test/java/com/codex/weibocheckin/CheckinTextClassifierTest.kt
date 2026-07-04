package com.codex.weibocheckin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckinTextClassifierTest {
    @Test
    fun successTextsAreRecognized() {
        assertEquals(
            PageState.SUCCESS,
            CheckinTextClassifier.classify(listOf("目标超话", "签到成功", "连续签到 12 天"))
        )
    }

    @Test
    fun riskTextsWinOverSuccessOrAlreadyDoneText() {
        assertEquals(
            PageState.RISK,
            CheckinTextClassifier.classify(listOf("验证码", "签到成功"))
        )
        assertEquals(
            PageState.RISK,
            CheckinTextClassifier.classify(listOf("请登录", "今日已签到"))
        )
    }

    @Test
    fun alreadyDoneTextsAreDistinctFromFreshSuccess() {
        assertEquals(
            PageState.ALREADY_DONE,
            CheckinTextClassifier.classify(listOf("目标超话", "今日已签到"))
        )
    }

    @Test
    fun riskTextsBlockAutomation() {
        assertEquals(
            PageState.RISK,
            CheckinTextClassifier.classify(listOf("请登录", "安全验证", "验证码", "风控", "滑块验证"))
        )
    }

    @Test
    fun failedTextsDoNotBecomeClickTargets() {
        assertEquals(
            PageState.FAILED,
            CheckinTextClassifier.classify(listOf("签到失败，请重试"))
        )
        assertFalse(CheckinTextClassifier.isCheckinCandidateText("签到失败"))
    }

    @Test
    fun checkinTextIsReadyToClick() {
        assertEquals(
            PageState.READY_TO_CLICK,
            CheckinTextClassifier.classify(listOf("热门", "签到"))
        )
    }

    @Test
    fun alreadyCheckedInIsNotClickableCandidate() {
        assertFalse(CheckinTextClassifier.isCheckinCandidateText("已签到"))
        assertFalse(CheckinTextClassifier.isCheckinCandidateText("签到规则"))
        assertFalse(CheckinTextClassifier.isCheckinCandidateText("签到榜"))
        assertFalse(CheckinTextClassifier.isCheckinCandidateText("签到提醒"))
        assertTrue(CheckinTextClassifier.isCheckinCandidateText("立即签到"))
    }
}
