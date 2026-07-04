package com.codex.weibocheckin

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity

class CheckinLaunchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        if (DeviceIdleChecker.currentState(this) == DeviceIdleChecker.DeviceIdleState.LOCKED_SECURE) {
            stopForKeyguard("安全锁屏仍未解除")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            if (keyguardManager.isKeyguardLocked) {
                keyguardManager.requestDismissKeyguard(
                    this,
                    object : KeyguardManager.KeyguardDismissCallback() {
                        override fun onDismissSucceeded() = startWeiboAndFinish()
                        override fun onDismissCancelled() = stopForKeyguard("锁屏解除被取消")
                        override fun onDismissError() = stopForKeyguard("系统拒绝解除锁屏")
                    }
                )
                return
            }
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        startWeiboAndFinish()
    }

    private fun startWeiboAndFinish() {
        WeiboLauncher.startCheckin(this)
        finish()
    }

    private fun stopForKeyguard(reason: String) {
        AppPreferences.stopAutomation(this)
        CheckinScheduler.cancelWatchdog(this)
        AppPreferences.setTodayStatus(this, CheckinStatus.NEEDS_ATTENTION, reason)
        AppPreferences.addLog(this, "启动前复检失败: $reason")
        NotificationHelper.notifyOpenCheckin(this, "需要解锁后继续签到", "手机仍处于锁屏状态，解锁后点按通知继续。")
        finish()
    }
}
