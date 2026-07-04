package com.codex.weibocheckin

enum class PageState {
    SUCCESS,
    ALREADY_DONE,
    RISK,
    FAILED,
    READY_TO_CLICK,
    UNKNOWN
}

object CheckinTextClassifier {
    private val alreadyDoneWords = listOf("已签到", "今日已签", "今日已签到")
    private val successWords = listOf("签到成功", "连续签到", "已连续签到")
    private val riskWords = listOf(
        "登录",
        "验证码",
        "安全验证",
        "账号异常",
        "访问受限",
        "操作频繁",
        "身份验证",
        "风控",
        "账号存在风险",
        "安全检测",
        "请完成验证",
        "滑块验证",
        "拖动滑块",
        "环境异常",
        "异常访问",
        "请稍后再试"
    )
    private val failureWords = listOf("签到失败", "签到未成功", "请重试")
    private val nonButtonWords = listOf("签到规则", "签到榜", "签到提醒", "签到失败", "今日已签到", "已签到")
    private val clickWords = listOf("签到", "立即签到")

    fun classify(texts: Collection<String>): PageState {
        val joined = texts.joinToString(separator = " ")
        if (riskWords.any { joined.contains(it) }) return PageState.RISK
        if (failureWords.any { joined.contains(it) }) return PageState.FAILED
        if (alreadyDoneWords.any { joined.contains(it) }) return PageState.ALREADY_DONE
        if (successWords.any { joined.contains(it) }) return PageState.SUCCESS
        if (texts.any { isCheckinCandidateText(it) }) return PageState.READY_TO_CLICK
        return PageState.UNKNOWN
    }

    fun isCheckinCandidateText(value: String?): Boolean {
        val text = value?.trim().orEmpty()
        if (text.isBlank()) return false
        if (nonButtonWords.any { text.contains(it) }) return false
        if (riskWords.any { text.contains(it) }) return false
        if (failureWords.any { text.contains(it) }) return false
        if (alreadyDoneWords.any { text.contains(it) }) return false
        if (successWords.any { text.contains(it) }) return false
        return clickWords.any { text == it }
    }
}
