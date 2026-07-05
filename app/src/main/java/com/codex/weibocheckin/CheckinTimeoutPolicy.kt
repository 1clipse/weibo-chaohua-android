package com.codex.weibocheckin

data class CheckinTimeoutResult(
    val status: CheckinStatus,
    val reason: String,
    val log: String
)

object CheckinTimeoutPolicy {
    fun classify(lastStage: String, preview: String, source: String): CheckinTimeoutResult {
        val sawWeiboPage = lastStage.contains(CheckinStage.ROOT_SEEN.label) || preview.isNotBlank()
        return if (sawWeiboPage) {
            CheckinTimeoutResult(
                status = CheckinStatus.FAILED,
                reason = "已打开微博但没有找到签到入口或成功状态",
                log = "超时: $source 已读取微博页面但未找到签到入口或成功状态，页面: ${preview.ifBlank { "无文本" }}"
            )
        } else {
            CheckinTimeoutResult(
                status = CheckinStatus.NEEDS_ATTENTION,
                reason = "打开微博后没有识别到页面，可能被锁屏、后台启动限制或系统省电拦截",
                log = "超时: $source 未收到有效页面事件，可能被系统拦截"
            )
        }
    }
}
