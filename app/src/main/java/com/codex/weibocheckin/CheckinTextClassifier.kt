package com.codex.weibocheckin

enum class PageState {
    SUCCESS,
    ALREADY_DONE,
    RISK,
    READY_TO_CLICK,
    UNKNOWN
}

object CheckinTextClassifier {
    private val alreadyDoneWords = listOf("已签到", "今日已签", "今日已签到")
    private val successWords = listOf("签到成功", "连续签到", "已连续签到")
    private val riskWords = listOf("登录", "验证码", "安全验证", "账号异常", "访问受限", "操作频繁", "身份验证")
    private val clickWords = listOf("签到", "立即签到")

    fun classify(texts: Collection<String>): PageState {
        val joined = texts.joinToString(separator = " ")
        if (alreadyDoneWords.any { joined.contains(it) }) return PageState.ALREADY_DONE
        if (successWords.any { joined.contains(it) }) return PageState.SUCCESS
        if (riskWords.any { joined.contains(it) }) return PageState.RISK
        if (texts.any { isCheckinCandidateText(it) }) return PageState.READY_TO_CLICK
        return PageState.UNKNOWN
    }

    fun isCheckinCandidateText(value: String?): Boolean {
        val text = value?.trim().orEmpty()
        if (text.isBlank()) return false
        if (alreadyDoneWords.any { text.contains(it) }) return false
        if (successWords.any { text.contains(it) }) return false
        return clickWords.any { text == it || text.contains(it) }
    }
}
