package com.codex.weibocheckin

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

class CheckinContinueActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppPreferences.addLog(this, "用户点按通知继续签到，重新执行预检查")
        sendBroadcast(
            Intent(this, CheckinAlarmReceiver::class.java)
                .setAction(AppConstants.ACTION_CONTINUE_CHECKIN)
        )
        finish()
    }
}
