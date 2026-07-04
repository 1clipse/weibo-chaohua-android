package com.codex.weibocheckin

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class WeiboAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastClickAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != AppConstants.WEIBO_PACKAGE) return
        if (!AppPreferences.isEnabled(this)) {
            AppPreferences.stopAutomation(this)
            CheckinScheduler.cancelWatchdog(this)
            return
        }
        if (!AppPreferences.automationActive(this)) return

        val now = System.currentTimeMillis()
        if (now > AppPreferences.automationDeadline(this)) {
            finish(
                status = CheckinStatus.FAILED,
                title = "微博签到超时",
                notification = "45 秒内没有确认签到状态。",
                log = "超时: 未识别到签到结果",
                reason = "45 秒内没有确认签到状态"
            )
            return
        }

        handler.removeCallbacks(scanRunnable)
        handler.postDelayed(scanRunnable, 250L)
    }

    override fun onInterrupt() = Unit

    private val scanRunnable = Runnable { scanCurrentWindow() }

    private fun scanCurrentWindow() {
        if (!AppPreferences.automationActive(this)) return
        if (isPastDeadline()) {
            finish(
                status = CheckinStatus.FAILED,
                title = "微博签到超时",
                notification = "45 秒内没有确认签到状态。",
                log = "超时: 扫描前已超过 45 秒截止时间",
                reason = "45 秒内没有确认签到状态"
            )
            return
        }
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != AppConstants.WEIBO_PACKAGE) return
        val texts = mutableListOf<String>()
        collectTexts(root, texts)

        when (CheckinTextClassifier.classify(texts)) {
            PageState.SUCCESS -> finish(
                status = CheckinStatus.SUCCESS,
                title = "微博超话签到完成",
                notification = "已识别到签到成功。",
                log = "成功: ${texts.preview()}",
                reason = ""
            )
            PageState.ALREADY_DONE -> finish(
                status = CheckinStatus.ALREADY_DONE,
                title = "微博超话今日已签到",
                notification = "已识别到今天已经签过到。",
                log = "已签到: ${texts.preview()}",
                reason = ""
            )
            PageState.RISK -> finish(
                status = CheckinStatus.NEEDS_ATTENTION,
                title = "微博签到需要处理",
                notification = "检测到登录、验证码或安全验证，请手动处理。",
                log = "阻断: ${texts.preview()}",
                reason = "检测到登录、验证码或安全验证"
            )
            PageState.FAILED -> finish(
                status = CheckinStatus.FAILED,
                title = "微博签到失败",
                notification = "微博页面提示签到失败，请手动确认。",
                log = "失败: ${texts.preview()}",
                reason = "微博页面提示签到失败"
            )
            PageState.READY_TO_CLICK -> {
                val now = System.currentTimeMillis()
                if (now - lastClickAt < AppConstants.CLICK_DEBOUNCE_MS) return
                if (isPastDeadline()) return
                val clicked = clickFirstCheckinNode(root)
                if (clicked) {
                    lastClickAt = now
                    AppPreferences.setTodayStatus(this, CheckinStatus.RUNNING)
                    AppPreferences.addLog(this, "已点击签到按钮")
                }
            }
            PageState.UNKNOWN -> Unit
        }
    }

    private fun finish(status: CheckinStatus, title: String, notification: String, log: String, reason: String) {
        AppPreferences.addLog(this, log)
        AppPreferences.setTodayStatus(this, status, reason)
        CheckinScheduler.cancelRetry(this)
        CheckinScheduler.cancelWatchdog(this)
        AppPreferences.stopAutomation(this)
        NotificationHelper.notify(this, title, notification)
    }

    private fun isPastDeadline(): Boolean =
        System.currentTimeMillis() > AppPreferences.automationDeadline(this)

    private fun collectTexts(node: AccessibilityNodeInfo?, out: MutableList<String>) {
        if (node == null) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        for (index in 0 until node.childCount) {
            collectTexts(node.getChild(index), out)
        }
    }

    private fun clickFirstCheckinNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val text = node.text?.toString() ?: node.contentDescription?.toString()
        if (CheckinTextClassifier.isCheckinCandidateText(text) && node.isVisibleToUser && node.isEnabled) {
            val clickable = findClickableAncestor(node)
            if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                return true
            }
        }
        for (index in 0 until node.childCount) {
            if (clickFirstCheckinNode(node.getChild(index))) return true
        }
        return false
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        while (current != null) {
            if (current.isClickable && current.isEnabled && current.isVisibleToUser) return current
            current = current.parent
        }
        return null
    }

    private fun List<String>.preview(): String =
        take(8).joinToString(" / ").take(180)
}
