package com.codex.weibocheckin

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity

class CheckinLaunchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (DeviceIdleChecker.currentState(this) == DeviceIdleChecker.DeviceIdleState.LOCKED_SECURE) {
            AppPreferences.stopAutomation(this)
            CheckinScheduler.cancelWatchdog(this)
            AppPreferences.setTodayStatus(this, CheckinStatus.NEEDS_ATTENTION, "安全锁屏仍未解除")
            AppPreferences.addLog(this, "启动前复检失败: 安全锁屏仍未解除")
            NotificationHelper.notifyOpenCheckin(this, "需要解锁后继续签到", "手机仍处于安全锁屏状态，解锁后点按通知继续。")
            finish()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        WeiboLauncher.startCheckin(this)
        finish()
    }
}
