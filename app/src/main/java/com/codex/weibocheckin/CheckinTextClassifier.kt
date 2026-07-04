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
    private val clickTextPatterns = listOf(
        Regex("^签到$"),
        Regex("^立即签到$"),
        Regex("^去签到$"),
        Regex("^马上签到$"),
        Regex("^签到打卡$"),
        Regex("^签到\\s*领积分$")
    )

    fun classify(texts: Collection<String>): PageState {
        val joined = texts.joinToString(separator = " ")
        if (riskWords.any { joined.contains(it) }) return PageState.RISK
        if (failureWords.any { joined.contains(it) }) return PageState.FAILED
        if (successWords.any { joined.contains(it) }) return PageState.SUCCESS
        if (alreadyDoneWords.any { joined.contains(it) }) return PageState.ALREADY_DONE
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
        return clickTextPatterns.any { it.matches(text) }
    }

    fun riskReason(texts: Collection<String>): String {
        val joined = texts.joinToString(separator = " ")
        return when {
            listOf("验证码", "滑块验证", "拖动滑块", "请完成验证").any { joined.contains(it) } ->
                "微博要求验证码或滑块验证"
            listOf("安全验证", "身份验证", "安全检测").any { joined.contains(it) } ->
                "微博要求安全验证"
            listOf("账号异常", "账号存在风险", "访问受限", "风控", "环境异常", "异常访问").any { joined.contains(it) } ->
                "微博提示账号或访问环境异常"
            listOf("操作频繁", "请稍后再试").any { joined.contains(it) } ->
                "微博提示操作频繁"
            joined.contains("登录") ->
                "微博登录状态失效"
            else -> "检测到登录、验证码或安全验证"
        }
    }
}
