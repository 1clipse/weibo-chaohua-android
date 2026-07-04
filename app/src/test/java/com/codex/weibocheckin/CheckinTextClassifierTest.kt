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
    fun freshSuccessWinsOverAlreadyDoneButtonState() {
        assertEquals(
            PageState.SUCCESS,
            CheckinTextClassifier.classify(listOf("签到成功", "已签到"))
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
    fun riskReasonExplainsTheMostUsefulManualAction() {
        assertEquals("微博登录状态失效", CheckinTextClassifier.riskReason(listOf("请登录")))
        assertEquals("微博要求验证码或滑块验证", CheckinTextClassifier.riskReason(listOf("拖动滑块")))
        assertEquals("微博要求安全验证", CheckinTextClassifier.riskReason(listOf("身份验证")))
        assertEquals("微博提示账号或访问环境异常", CheckinTextClassifier.riskReason(listOf("账号异常")))
        assertEquals("微博提示操作频繁", CheckinTextClassifier.riskReason(listOf("操作频繁")))
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

    @Test
    fun commonCheckinButtonLabelsAreClickableCandidates() {
        assertTrue(CheckinTextClassifier.isCheckinCandidateText("去签到"))
        assertTrue(CheckinTextClassifier.isCheckinCandidateText("马上签到"))
        assertTrue(CheckinTextClassifier.isCheckinCandidateText("签到打卡"))
        assertTrue(CheckinTextClassifier.isCheckinCandidateText("签到领积分"))
        assertTrue(CheckinTextClassifier.isCheckinCandidateText("签到 领积分"))
    }

    @Test
    fun looseCheckinTextsAreNotClickableCandidates() {
        assertFalse(CheckinTextClassifier.isCheckinCandidateText("超话签到"))
        assertFalse(CheckinTextClassifier.isCheckinCandidateText("未签到"))
        assertFalse(CheckinTextClassifier.isCheckinCandidateText("签到任务"))
    }
}
