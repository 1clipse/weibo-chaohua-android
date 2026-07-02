package com.codex.weibocheckin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckinTextClassifierTest {
    @Test
    fun successTextsWin() {
        assertEquals(
            PageState.SUCCESS,
            CheckinTextClassifier.classify(listOf("林俊杰超话", "签到成功", "连续签到 12 天"))
        )
    }

    @Test
    fun alreadyDoneTextsAreDistinctFromFreshSuccess() {
        assertEquals(
            PageState.ALREADY_DONE,
            CheckinTextClassifier.classify(listOf("林俊杰超话", "今日已签到"))
        )
    }

    @Test
    fun riskTextsBlockAutomation() {
        assertEquals(
            PageState.RISK,
            CheckinTextClassifier.classify(listOf("请登录", "安全验证", "验证码"))
        )
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
        assertTrue(CheckinTextClassifier.isCheckinCandidateText("立即签到"))
    }
}
