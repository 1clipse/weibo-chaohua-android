package com.codex.weibocheckin

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class WeiboAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val idleSignalReceiver = IdleSignalReceiver()
    private var lastClickAt = 0L
    private var idleSignalReceiverRegistered = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerIdleSignalReceiver()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != AppConstants.WEIBO_PACKAGE) return
        if (!AppPreferences.isEnabled(this)) {
            AppPreferences.stopAutomation(this)
            CheckinScheduler.cancelWatchdog(this)
            return
        }
        if (!AppPreferences.automationActive(this)) return
        AppPreferences.setLastStage(this, CheckinStage.ACCESSIBILITY_EVENT_RECEIVED)

        val now = System.currentTimeMillis()
        if (now > AppPreferences.automationDeadline(this)) {
            CheckinTimeoutHandler.handleIfExpired(this, "无障碍事件超时")
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
            CheckinTimeoutHandler.handleIfExpired(this, "无障碍扫描超时")
            return
        }
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != AppConstants.WEIBO_PACKAGE) return
        AppPreferences.setLastStage(this, CheckinStage.ROOT_SEEN)
        val texts = mutableListOf<String>()
        collectTexts(root, texts)
        val preview = texts.preview()
        val importantPreview = texts.importantPreview()
        AppPreferences.setLastAccessibilityPreview(this, preview)

        when (CheckinTextClassifier.classify(texts)) {
            PageState.SUCCESS -> finish(
                status = CheckinStatus.SUCCESS,
                title = "微博超话签到完成",
                notification = "已识别到签到成功。",
                log = "成功: $preview",
                reason = ""
            )
            PageState.ALREADY_DONE -> finish(
                status = CheckinStatus.ALREADY_DONE,
                title = "微博超话今日已签到",
                notification = "已识别到今天已经签过到。",
                log = "已签到: $preview",
                reason = ""
            )
            PageState.RISK -> {
                val riskReason = CheckinTextClassifier.riskReason(texts)
                finish(
                    status = CheckinStatus.NEEDS_ATTENTION,
                    title = "微博签到需要处理",
                    notification = "$riskReason，请手动处理。",
                    log = "阻断: $riskReason / ${importantPreview.ifBlank { preview }} / 页面: $preview",
                    reason = riskReason
                )
            }
            PageState.FAILED -> finish(
                status = CheckinStatus.FAILED,
                title = "微博签到失败",
                notification = "微博页面提示签到失败，请手动确认。",
                log = "失败: ${importantPreview.ifBlank { preview }} / 页面: $preview",
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
                    AppPreferences.setLastStage(this, CheckinStage.CLICK_SENT)
                    AppPreferences.addLog(this, "已点击签到按钮")
                }
            }
            PageState.UNKNOWN -> Unit
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(scanRunnable)
        unregisterIdleSignalReceiver()
        super.onDestroy()
    }

    private fun finish(status: CheckinStatus, title: String, notification: String, log: String, reason: String) {
        AppPreferences.addLog(this, log)
        AppPreferences.setLastStage(this, CheckinStage.FINISHED, status.name)
        AppPreferences.setTodayStatus(this, status, reason)
        CheckinScheduler.cancelRetry(this)
        CheckinScheduler.cancelWatchdog(this)
        AppPreferences.clearDeferredCheckinState(this)
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

    private fun List<String>.importantPreview(): String {
        val keywords = listOf(
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
            "滑块验证",
            "拖动滑块",
            "环境异常",
            "异常访问",
            "请稍后再试",
            "签到失败",
            "签到未成功",
            "请重试"
        )
        return filter { text -> keywords.any { text.contains(it) } }
            .take(4)
            .joinToString(" / ")
            .take(180)
    }

    private fun registerIdleSignalReceiver() {
        if (idleSignalReceiverRegistered) return
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(idleSignalReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(idleSignalReceiver, filter)
        }
        idleSignalReceiverRegistered = true
    }

    private fun unregisterIdleSignalReceiver() {
        if (!idleSignalReceiverRegistered) return
        runCatching { unregisterReceiver(idleSignalReceiver) }
        idleSignalReceiverRegistered = false
    }
}
