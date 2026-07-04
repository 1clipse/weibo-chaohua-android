package com.codex.weibocheckin

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.ensureChannel(this)
        CheckinTimeoutHandler.handleIfExpired(this, "打开 App 恢复检查")
        setContent {
            var darkMode by remember { mutableStateOf(AppPreferences.darkMode(this)) }
            CheckinTheme(darkMode = darkMode) {
                SettingsScreen(
                    darkMode = darkMode,
                    onDarkModeChange = {
                        darkMode = it
                        AppPreferences.setDarkMode(this, it)
                    }
                )
            }
        }
    }
}

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1F2937),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF138A55),
    tertiary = Color(0xFFB7791F),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFE2E8F0)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF22C55E),
    onPrimary = Color(0xFF052E16),
    secondary = Color(0xFF34D399),
    tertiary = Color(0xFFFBBF24),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF172033),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF334155)
)

private val PanelShape = RoundedCornerShape(10.dp)
private val CompactShape = RoundedCornerShape(8.dp)
private val StoredTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
private val LogTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")

@Composable
private fun CheckinTheme(
    darkMode: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkMode) DarkScheme else LightScheme,
        content = content
    )
}

@Preview(name = "Day", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SettingsScreenDayPreview() {
    CheckinTheme(darkMode = false) {
        SettingsContent(
            state = previewState(darkMode = false),
            onEnabledChange = {},
            onDarkModeChange = {},
            onUrlChange = {},
            onTimeChange = {},
            onManualTest = {},
            onScheduleTest = {},
            onOpenAccessibility = {},
            onOpenExactAlarm = {},
            onOpenNotification = {},
            onOpenFullScreenIntent = {},
            onOpenBatteryOptimization = {},
            onClearLogs = {}
        )
    }
}

@Preview(name = "Night", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SettingsScreenNightPreview() {
    CheckinTheme(darkMode = true) {
        SettingsContent(
            state = previewState(darkMode = true),
            onEnabledChange = {},
            onDarkModeChange = {},
            onUrlChange = {},
            onTimeChange = {},
            onManualTest = {},
            onScheduleTest = {},
            onOpenAccessibility = {},
            onOpenExactAlarm = {},
            onOpenNotification = {},
            onOpenFullScreenIntent = {},
            onOpenBatteryOptimization = {},
            onClearLogs = {}
        )
    }
}

@Composable
private fun SettingsScreen(
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refresh by remember { mutableStateOf(0) }
    var enabled by remember(refresh) { mutableStateOf(AppPreferences.isEnabled(context)) }
    var url by remember(refresh) { mutableStateOf(AppPreferences.chaohuaUrl(context)) }
    var timeText by remember(refresh) {
        mutableStateOf("%02d:%02d".format(AppPreferences.hour(context), AppPreferences.minute(context)))
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                CheckinTimeoutHandler.handleIfExpired(context, "页面恢复检查")
                disableDailyIfBlockingPrerequisiteMissing(context)
                refresh++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3_000L)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                CheckinTimeoutHandler.handleIfExpired(context, "前台状态刷新")
                refresh++
            }
        }
    }
    val weiboStatus = WeiboAppChecker.currentStatus(context)

    SettingsContent(
        state = UiState(
            enabled = enabled,
            darkMode = darkMode,
            url = url,
            timeText = timeText,
            nextRun = nextRunText(context, enabled),
            todayStatus = AppPreferences.todayStatus(context),
            lastAttempt = AppPreferences.lastAttempt(context),
            temporaryTestAt = AppPreferences.temporaryTestAt(context),
            nextRetry = AppPreferences.nextRetry(context),
            idleDeadline = AppPreferences.idleDeadline(context),
            failureReason = AppPreferences.failureReason(context),
            accessibilityEnabled = AccessibilityStatusChecker.isServiceEnabled(context),
            exactAlarmGranted = CheckinScheduler.canScheduleExact(context),
            notificationsGranted = NotificationHelper.canNotify(context),
            fullScreenIntentGranted = NotificationHelper.canUseLockscreenLaunch(context),
            batteryOptimizationIgnored = BatteryOptimizationChecker.isIgnoringBatteryOptimizations(context),
            deviceState = DeviceIdleChecker.currentState(context).label(),
            weiboInstalled = weiboStatus.installed,
            weiboVersion = weiboStatus.versionName,
            weiboCanOpenUrl = weiboStatus.canOpenConfiguredUrl,
            automationActive = AppPreferences.automationActive(context),
            automationDeadline = AppPreferences.automationDeadline(context),
            lastStage = AppPreferences.lastStage(context),
            lastStageAt = AppPreferences.lastStageAt(context),
            lastAccessibilityPreview = AppPreferences.lastAccessibilityPreview(context),
            logs = AppPreferences.logs(context)
        ),
        onEnabledChange = { checked ->
            if (checked && !CheckinScheduler.canScheduleExact(context)) {
                enabled = false
                AppPreferences.setEnabled(context, false)
                CheckinScheduler.cancelDailySchedule(context)
                AppPreferences.addLog(context, "每日签到未开启: 请先开启精确闹钟权限")
                openExactAlarmSettings(context)
            } else if (checked && !NotificationHelper.canNotify(context)) {
                enabled = false
                AppPreferences.setEnabled(context, false)
                CheckinScheduler.cancelDailySchedule(context)
                AppPreferences.addLog(context, "每日签到未开启: 请先开启通知权限")
                if (Build.VERSION.SDK_INT >= 33) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openNotificationSettings(context)
                }
            } else {
                enabled = checked
                AppPreferences.setEnabled(context, checked)
                if (checked) CheckinScheduler.scheduleNext(context) else CheckinScheduler.cancel(context)
            }
            refresh++
        },
        onDarkModeChange = onDarkModeChange,
        onUrlChange = {
            url = it
            AppPreferences.setChaohuaUrl(context, it)
        },
        onTimeChange = { value ->
            timeText = value
            parseTime(value)?.let { (hour, minute) ->
                AppPreferences.setTime(context, hour, minute)
                if (AppPreferences.isEnabled(context)) CheckinScheduler.scheduleNext(context)
                refresh++
            }
        },
        onManualTest = {
            val currentWeiboStatus = WeiboAppChecker.currentStatus(context)
            val blockers = manualTestBlockerLabels(
                weiboInstalled = currentWeiboStatus.installed,
                weiboCanOpenUrl = currentWeiboStatus.canOpenConfiguredUrl,
                notificationsGranted = NotificationHelper.canNotify(context),
                accessibilityEnabled = AccessibilityStatusChecker.isServiceEnabled(context)
            )
            if (blockers.isNotEmpty()) {
                AppPreferences.addLog(context, "手动测试未启动: 还需处理 ${blockers.joinToString("、")}")
            } else {
                AppPreferences.addLog(context, "手动测试触发")
                WeiboLauncher.startCheckin(context)
            }
            refresh++
        },
        onScheduleTest = {
            if (AppPreferences.isEnabled(context)) {
                CheckinScheduler.scheduleTemporaryTest(context)
            } else {
                AppPreferences.addLog(context, "临时定时测试未安排: 每日签到开关未开启")
            }
            refresh++
        },
        onOpenAccessibility = {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        },
        onOpenExactAlarm = {
            openExactAlarmSettings(context)
        },
        onOpenNotification = {
            if (Build.VERSION.SDK_INT >= 33) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openNotificationSettings(context)
            }
        },
        onOpenFullScreenIntent = {
            openFullScreenIntentSettings(context)
        },
        onOpenBatteryOptimization = {
            openBatteryOptimizationSettings(context)
        },
        onClearLogs = {
            if (AppPreferences.automationActive(context)) {
                AppPreferences.addLog(context, "自动化运行中，暂不重置诊断")
            } else {
                val now = LocalDateTime.now()
                if (!DiagnosticResetPolicy.shouldKeepDeferredState(
                        todayStatus = AppPreferences.todayStatus(context),
                        nextRetry = AppPreferences.nextRetry(context),
                        idleDeadline = AppPreferences.idleDeadline(context),
                        now = now
                    )
                ) {
                    CheckinScheduler.cancelRetry(context)
                    CheckinScheduler.cancelTemporaryTest(context)
                }
                if (!AppPreferences.clearDiagnostics(context, now)) {
                    AppPreferences.addLog(context, "自动化运行中，暂不重置诊断")
                } else if (AppPreferences.isEnabled(context)) {
                    CheckinScheduler.scheduleNext(context)
                }
            }
            refresh++
        }
    )
}

@Composable
private fun SettingsContent(
    state: UiState,
    onEnabledChange: (Boolean) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onUrlChange: (String) -> Unit,
    onTimeChange: (String) -> Unit,
    onManualTest: () -> Unit,
    onScheduleTest: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenExactAlarm: () -> Unit,
    onOpenNotification: () -> Unit,
    onOpenFullScreenIntent: () -> Unit,
    onOpenBatteryOptimization: () -> Unit,
    onClearLogs: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(Modifier.height(10.dp)) }
            item { Header(state) }
            item {
                RunPanel(
                    state = state,
                    onEnabledChange = onEnabledChange,
                    onDarkModeChange = onDarkModeChange
                )
            }
            item {
                ResultPanel(state)
            }
            item {
                PermissionPanel(
                    state = state,
                    onOpenAccessibility = onOpenAccessibility,
                    onOpenExactAlarm = onOpenExactAlarm,
                    onOpenNotification = onOpenNotification,
                    onOpenFullScreenIntent = onOpenFullScreenIntent,
                    onOpenBatteryOptimization = onOpenBatteryOptimization
                )
            }
            item {
                SettingsPanel(
                    url = state.url,
                    timeText = state.timeText,
                    onUrlChange = onUrlChange,
                    onTimeChange = onTimeChange
                )
            }
            item {
                ActionPanel(state = state, onManualTest = onManualTest, onScheduleTest = onScheduleTest)
            }
            item {
                LogHeader(onClearLogs = onClearLogs)
            }
            if (state.logs.isEmpty()) {
                item {
                    EmptyLog()
                }
            } else {
                items(state.logs) { line ->
                    LogRow(line)
                }
            }
            item {
                Watermark()
            }
        }
    }
}

@Composable
private fun Header(state: UiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Text(
                text = "微博超话签到助手",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (state.enabled) "每天 ${state.timeText} 尝试签到" else "签到尝试未启用",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        StatePill(status = state.todayStatus)
    }
}

@Composable
private fun RunPanel(
    state: UiState,
    onEnabledChange: (Boolean) -> Unit,
    onDarkModeChange: (Boolean) -> Unit
) {
    Panel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("每日签到", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    state.nextRun,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = state.enabled, onCheckedChange = onEnabledChange)
        }
        DividerLine()
        ToggleRow(
            title = "夜间模式",
            description = if (state.darkMode) "深色界面已启用" else "白天界面已启用",
            checked = state.darkMode,
            onCheckedChange = onDarkModeChange
        )
    }
}

@Composable
private fun StatePill(status: String) {
    val color = statusColor(status)
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.36f))
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 7.dp)
                .semantics { contentDescription = "今日状态 ${statusLabel(status)}" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.size(7.dp).background(color, CircleShape))
            Text(
                text = statusLabel(status),
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ResultPanel(state: UiState) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        copied = false
    }
    Panel {
        SectionTitle("今日结果", "为避免打扰，会等手机空闲后再打开微博")
        StatusSummary(status = statusLabel(state.todayStatus), detail = statusDetail(state))
        InfoRow("最近尝试", state.lastAttempt.displayStoredTimeOr("尚未触发"))
        InfoRow("临时测试", state.temporaryTestAt.displayStoredTimeOr("当前未安排"))
        InfoRow("当前设备", state.deviceState)
        InfoRow("下次重试", state.nextRetry.displayStoredTimeOr("当前未安排"))
        InfoRow("截止时间", state.idleDeadline.displayStoredTimeOr("到点后显示"))
        InfoRow("自动化", automationText(state.automationActive, state.automationDeadline))
        InfoRow("最后阶段", if (state.lastStage.isBlank()) "暂无记录" else stageText(state.lastStage, state.lastStageAt))
        InfoRow("最近识别", state.lastAccessibilityPreview.ifBlank { "本轮尚未读取微博页面" })
        InfoRow("最近日志", state.logs.firstOrNull()?.let(::displayLogLine) ?: "暂无日志")
        TextButton(
            onClick = {
                clipboardManager.setText(AnnotatedString(diagnosticText(state)))
                copied = true
            },
            shape = CompactShape
        ) {
            Text(if (copied) "已复制诊断" else "复制诊断")
        }
    }
}

@Composable
private fun LogHeader(onClearLogs: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "最近日志",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        TextButton(onClick = onClearLogs, shape = CompactShape) {
            Text("重置诊断")
        }
    }
}

@Composable
private fun StatusSummary(status: String, detail: String) {
    val color = statusColorFromLabel(status)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CompactShape,
        color = color.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(10.dp).padding(top = 4.dp).background(color, CircleShape))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(status, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PermissionPanel(
    state: UiState,
    onOpenAccessibility: () -> Unit,
    onOpenExactAlarm: () -> Unit,
    onOpenNotification: () -> Unit,
    onOpenFullScreenIntent: () -> Unit,
    onOpenBatteryOptimization: () -> Unit
) {
    Panel {
        SectionTitle("权限状态", "开启后才能定时、通知和读取微博页面")
        StatusRow(
            label = "无障碍",
            value = if (state.accessibilityEnabled) "已开启" else "未开启",
            healthy = state.accessibilityEnabled,
            action = "设置",
            onClick = onOpenAccessibility
        )
        StatusRow(
            label = "精确闹钟",
            value = if (state.exactAlarmGranted) "可用" else "未开启",
            healthy = state.exactAlarmGranted,
            action = "设置",
            onClick = onOpenExactAlarm
        )
        StatusRow(
            label = "通知",
            value = if (state.notificationsGranted) "已开启" else "未开启",
            healthy = state.notificationsGranted,
            action = "设置",
            onClick = onOpenNotification
        )
        StatusRow(
            label = "锁屏启动",
            value = if (state.fullScreenIntentGranted) "可用" else "受限制",
            healthy = state.fullScreenIntentGranted,
            action = "设置",
            onClick = onOpenFullScreenIntent
        )
        StatusRow(
            label = "省电限制",
            value = if (state.batteryOptimizationIgnored) "已放行" else "可能拦截",
            healthy = state.batteryOptimizationIgnored,
            action = "设置",
            onClick = onOpenBatteryOptimization
        )
        StatusRow(
            label = "微博 App",
            value = weiboStatusText(state),
            healthy = state.weiboInstalled && state.weiboCanOpenUrl,
            action = null,
            onClick = null
        )
    }
}

@Composable
private fun SettingsPanel(
    url: String,
    timeText: String,
    onUrlChange: (String) -> Unit,
    onTimeChange: (String) -> Unit
) {
    Panel {
        SectionTitle("设置", "可配置目标超话和每日尝试时间：$timeText")
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text("超话 URL") },
            modifier = Modifier.fillMaxWidth(),
            shape = CompactShape,
            singleLine = false,
            maxLines = 3
        )
        TimeWheelField(timeText = timeText, onTimeChange = onTimeChange)
    }
}

@Composable
private fun TimeWheelField(
    timeText: String,
    onTimeChange: (String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val (initialHour, initialMinute) = parseTime(timeText) ?: (10 to 0)

    if (showPicker) {
        var selectedHour by remember(timeText) { mutableStateOf(initialHour) }
        var selectedMinute by remember(timeText) { mutableStateOf(initialMinute) }
        var hourScrolling by remember(timeText) { mutableStateOf(false) }
        var minuteScrolling by remember(timeText) { mutableStateOf(false) }
        val canSave = !hourScrolling && !minuteScrolling
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("选择每日尝试时间") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "滚动数字，停在中间的时间会自动高亮。最晚可选 22:59，23:00 是当天截止时间。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "将保存 %02d:%02d".format(selectedHour, selectedMinute),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TimeWheelColumn(
                            title = "小时",
                            values = AppConstants.SCHEDULABLE_HOURS.toList(),
                            selectedValue = selectedHour,
                            onValueSelected = { selectedHour = it },
                            onScrollInProgressChange = { hourScrolling = it },
                            modifier = Modifier.weight(1f)
                        )
                        TimeWheelColumn(
                            title = "分钟",
                            values = (0..59).toList(),
                            selectedValue = selectedMinute,
                            onValueSelected = { selectedMinute = it },
                            onScrollInProgressChange = { minuteScrolling = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTimeChange("%02d:%02d".format(selectedHour, selectedMinute))
                        showPicker = false
                    },
                    enabled = canSave,
                    shape = CompactShape
                ) {
                    Text(if (canSave) "保存时间" else "滚动中")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }, shape = CompactShape) {
                    Text("取消")
                }
            }
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "每日尝试时间，当前 $timeText，点击选择" }
            .clickable { showPicker = true },
        shape = CompactShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    "每日尝试时间",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(timeText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = { showPicker = true }, shape = CompactShape) {
                Text("选择")
            }
        }
    }
}

@Composable
private fun TimeWheelColumn(
    title: String,
    values: List<Int>,
    selectedValue: Int,
    onValueSelected: (Int) -> Unit,
    onScrollInProgressChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val paddedValues = remember(values) { listOf<Int?>(null, null) + values + listOf(null, null) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = values.indexOf(selectedValue).coerceAtLeast(0)
    )
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val centeredValue by remember(paddedValues, listState) {
        derivedStateOf {
            paddedValues[(listState.firstVisibleItemIndex + 2).coerceIn(paddedValues.indices)]
        }
    }

    LaunchedEffect(centeredValue) {
        centeredValue?.let(onValueSelected)
    }
    LaunchedEffect(listState.isScrollInProgress) {
        onScrollInProgressChange(listState.isScrollInProgress)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CompactShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            LazyColumn(
                state = listState,
                flingBehavior = flingBehavior,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(216.dp),
                contentPadding = PaddingValues(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(paddedValues) { value ->
                    if (value == null) {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                        )
                        return@items
                    }
                    val selected = value == centeredValue
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp)
                            .height(38.dp)
                            .clickable {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(values.indexOf(value).coerceAtLeast(0))
                                }
                            },
                        shape = CompactShape,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            Color.Transparent
                        },
                        border = if (selected) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.36f))
                        } else {
                            null
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "%02d".format(value),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionPanel(
    state: UiState,
    onManualTest: () -> Unit,
    onScheduleTest: () -> Unit
) {
    val blockers = manualTestBlockers(state)
    val warnings = manualTestWarnings(state)
    val canTest = blockers.isEmpty() && !state.automationActive
    val canScheduleTest = canTest && state.enabled
    Panel {
        SectionTitle("测试", "可立即测试，也可 2 分钟后走一次真实定时链路")
        if (blockers.isNotEmpty()) {
            Text(
                "还需处理：${blockers.joinToString("、")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        } else if (warnings.isNotEmpty()) {
            Text(
                "建议检查：${warnings.joinToString("、")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                if (state.enabled) "已具备测试条件。" else "如需测试定时链路，请先开启每日签到。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .semantics { contentDescription = "手动测试签到，立即打开微博测试签到流程" },
            shape = CompactShape,
            enabled = canTest,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            onClick = onManualTest
        ) {
            Text(if (state.automationActive) "测试运行中" else "手动测试签到")
        }
        TextButton(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .semantics { contentDescription = "安排 2 分钟后临时定时测试" },
            shape = CompactShape,
            enabled = canScheduleTest,
            onClick = onScheduleTest
        ) {
            Text("2 分钟后定时测试")
        }
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    healthy: Boolean,
    action: String?,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (healthy) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                    shape = CircleShape
                )
        )
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (action != null && onClick != null) {
            TextButton(onClick = onClick, shape = CompactShape) {
                Text(action)
            }
        }
    }
}

@Composable
private fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline)
    )
}

@Composable
private fun EmptyLog() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            "暂无日志",
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LogRow(line: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            displayLogLine(line),
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Watermark() {
    Text(
        text = "create by kimziyi",
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
    )
}

private data class UiState(
    val enabled: Boolean,
    val darkMode: Boolean,
    val url: String,
    val timeText: String,
    val nextRun: String,
    val todayStatus: String,
    val lastAttempt: String,
    val temporaryTestAt: String,
    val nextRetry: String,
    val idleDeadline: String,
    val failureReason: String,
    val accessibilityEnabled: Boolean,
    val exactAlarmGranted: Boolean,
    val notificationsGranted: Boolean,
    val fullScreenIntentGranted: Boolean,
    val batteryOptimizationIgnored: Boolean,
    val deviceState: String,
    val weiboInstalled: Boolean,
    val weiboVersion: String,
    val weiboCanOpenUrl: Boolean,
    val automationActive: Boolean,
    val automationDeadline: Long,
    val lastStage: String,
    val lastStageAt: Long,
    val lastAccessibilityPreview: String,
    val logs: List<String>
)

private fun previewState(darkMode: Boolean) = UiState(
    enabled = true,
    darkMode = darkMode,
    url = AppConstants.DEFAULT_CHAOHUA_URL,
    timeText = "10:00",
    nextRun = "下次尝试: 2026-07-03 10:00",
    todayStatus = CheckinStatus.WAITING_FOR_IDLE.name,
    lastAttempt = "2026-07-02T10:00",
    temporaryTestAt = "2026-07-02T10:02",
    nextRetry = "2026-07-02T10:15",
    idleDeadline = "2026-07-02T23:00",
    failureReason = "",
    accessibilityEnabled = true,
    exactAlarmGranted = true,
    notificationsGranted = false,
    fullScreenIntentGranted = true,
    batteryOptimizationIgnored = false,
    deviceState = "待机，可自动尝试",
    weiboInstalled = true,
    weiboVersion = "13.0.0",
    weiboCanOpenUrl = true,
    automationActive = true,
    automationDeadline = System.currentTimeMillis() + 30_000L,
    lastStage = "已请求打开微博",
    lastStageAt = System.currentTimeMillis(),
    lastAccessibilityPreview = "超话 / 签到",
    logs = listOf(
        "2026-07-02T10:00:00Z  手动测试触发",
        "2026-07-02T10:00:03Z  已打开微博超话，等待无障碍服务识别"
    )
)

private fun statusLabel(status: String): String =
    when (runCatching { CheckinStatus.valueOf(status) }.getOrDefault(CheckinStatus.NOT_RUN)) {
        CheckinStatus.NOT_RUN -> "未运行"
        CheckinStatus.WAITING_FOR_IDLE -> "等待手机空闲"
        CheckinStatus.RUNNING -> "正在尝试签到"
        CheckinStatus.SUCCESS -> "签到成功"
        CheckinStatus.ALREADY_DONE -> "今日已签到"
        CheckinStatus.FAILED -> "签到失败"
        CheckinStatus.NEEDS_ATTENTION -> "需要人工处理"
    }

private fun statusColor(status: String): Color =
    when (runCatching { CheckinStatus.valueOf(status) }.getOrDefault(CheckinStatus.NOT_RUN)) {
        CheckinStatus.SUCCESS, CheckinStatus.ALREADY_DONE -> Color(0xFF22C55E)
        CheckinStatus.WAITING_FOR_IDLE -> Color(0xFFF59E0B)
        CheckinStatus.RUNNING -> Color(0xFF3B82F6)
        CheckinStatus.FAILED, CheckinStatus.NEEDS_ATTENTION -> Color(0xFFEF4444)
        CheckinStatus.NOT_RUN -> Color(0xFF64748B)
    }

private fun statusColorFromLabel(label: String): Color =
    when (label) {
        "签到成功", "今日已签到" -> Color(0xFF22C55E)
        "等待手机空闲" -> Color(0xFFF59E0B)
        "正在尝试签到" -> Color(0xFF3B82F6)
        "签到失败", "需要人工处理" -> Color(0xFFEF4444)
        else -> Color(0xFF64748B)
    }

private fun statusDetail(state: UiState): String =
    when (runCatching { CheckinStatus.valueOf(state.todayStatus) }.getOrDefault(CheckinStatus.NOT_RUN)) {
        CheckinStatus.NOT_RUN -> "到点后会先判断手机是否空闲。"
        CheckinStatus.WAITING_FOR_IDLE -> "手机正在使用，暂不跳转微博；锁屏或息屏后会尽快重试。"
        CheckinStatus.RUNNING -> "已检测到手机空闲，正在打开微博尝试签到。"
        CheckinStatus.SUCCESS -> "今天的超话签到已经完成。"
        CheckinStatus.ALREADY_DONE -> "微博页面显示今天已经签过到。"
        CheckinStatus.FAILED -> state.failureReason.ifBlank { "今天未确认签到完成。" }
        CheckinStatus.NEEDS_ATTENTION -> state.failureReason.ifBlank { "微博需要登录、验证码或安全验证。" }
    }

private fun parseTime(value: String): Pair<Int, Int>? {
    val parts = value.split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (!ScheduleTimePolicy.isAllowed(hour, minute)) return null
    return hour to minute
}

private fun nextRunText(context: Context, enabled: Boolean): String {
    if (!enabled) return "未启用"
    val scheduledAt = AppPreferences.nextDailyScheduledAt(context)
    if (scheduledAt.isBlank()) return "尚未安排"
    return "下次尝试: ${displayStoredTime(scheduledAt)}"
}

private fun DeviceIdleChecker.DeviceIdleState.label(): String =
    when (this) {
        DeviceIdleChecker.DeviceIdleState.ACTIVE -> "正在使用，暂不打扰"
        DeviceIdleChecker.DeviceIdleState.IDLE_UNLOCKABLE -> "待机，可自动尝试"
        DeviceIdleChecker.DeviceIdleState.LOCKED_SECURE -> "安全锁屏，需要解锁"
    }

private fun displayStoredTime(value: String): String =
    runCatching {
        LocalDateTime.parse(value).format(StoredTimeFormatter)
    }.getOrDefault(value)

private fun String.displayStoredTimeOr(fallback: String): String =
    if (isBlank()) fallback else displayStoredTime(this)

private fun displayEpochMillis(value: Long): String =
    if (value <= 0L) "" else runCatching {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(value), ZoneId.systemDefault()).format(StoredTimeFormatter)
    }.getOrDefault("")

private fun displayLogLine(line: String): String {
    val parts = line.split("  ", limit = 2)
    if (parts.size != 2) return line
    val localTime = runCatching {
        LocalDateTime.ofInstant(Instant.parse(parts[0]), ZoneId.systemDefault()).format(LogTimeFormatter)
    }.getOrDefault(parts[0])
    return "$localTime  ${parts[1]}"
}

private fun automationText(active: Boolean, deadline: Long): String {
    if (!active) return "未运行"
    val remainingSeconds = ((deadline - System.currentTimeMillis()).coerceAtLeast(0L) / 1000L)
    val deadlineText = displayEpochMillis(deadline)
    return if (deadlineText.isBlank()) "运行中" else "运行中，约 ${remainingSeconds}s 后超时"
}

private fun stageText(stage: String, at: Long): String {
    val atText = displayEpochMillis(at)
    return if (atText.isBlank()) stage else "$stage · $atText"
}

private fun weiboStatusText(state: UiState): String =
    when {
        !state.weiboInstalled -> "未安装"
        !state.weiboCanOpenUrl -> "URL 不可打开"
        state.weiboVersion.isBlank() -> "已安装"
        else -> "已安装 ${state.weiboVersion}"
    }

private fun diagnosticText(state: UiState): String {
    val lines = mutableListOf<String>()
    lines += "微博超话签到助手诊断"
    lines += "App 版本: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    lines += "今日状态: ${statusLabel(state.todayStatus)}"
    lines += "失败原因: ${state.failureReason.ifBlank { "无" }}"
    lines += "当前设备: ${state.deviceState}"
    lines += "微博 App: ${weiboStatusText(state)}"
    lines += "无障碍: ${if (state.accessibilityEnabled) "已开启" else "未开启"}"
    lines += "精确闹钟: ${if (state.exactAlarmGranted) "可用" else "未开启"}"
    lines += "通知: ${if (state.notificationsGranted) "已开启" else "未开启"}"
    lines += "锁屏启动: ${if (state.fullScreenIntentGranted) "可用" else "受限制"}"
    lines += "省电限制: ${if (state.batteryOptimizationIgnored) "已放行" else "可能拦截"}"
    lines += "自动化: ${automationText(state.automationActive, state.automationDeadline)}"
    lines += "最后阶段: ${stageText(state.lastStage.ifBlank { "无" }, state.lastStageAt)}"
    lines += "最近识别: ${state.lastAccessibilityPreview.ifBlank { "无" }}"
    if (state.lastAttempt.isNotBlank()) lines += "最近尝试: ${displayStoredTime(state.lastAttempt)}"
    if (state.temporaryTestAt.isNotBlank()) lines += "临时测试: ${displayStoredTime(state.temporaryTestAt)}"
    if (state.nextRetry.isNotBlank()) lines += "下次重试: ${displayStoredTime(state.nextRetry)}"
    if (state.idleDeadline.isNotBlank()) lines += "截止时间: ${displayStoredTime(state.idleDeadline)}"
    lines += "最近日志:"
    val logs = state.logs.take(8)
    if (logs.isEmpty()) {
        lines += "无"
    } else {
        lines += logs.map(::displayLogLine)
    }
    return lines.joinToString("\n")
}

private fun manualTestBlockers(state: UiState): List<String> =
    manualTestBlockerLabels(
        weiboInstalled = state.weiboInstalled,
        weiboCanOpenUrl = state.weiboCanOpenUrl,
        notificationsGranted = state.notificationsGranted,
        accessibilityEnabled = state.accessibilityEnabled
    )

private fun manualTestBlockerLabels(
    weiboInstalled: Boolean,
    weiboCanOpenUrl: Boolean,
    notificationsGranted: Boolean,
    accessibilityEnabled: Boolean
): List<String> =
    CheckinPrerequisitePolicy.blockers(
        weiboInstalled = weiboInstalled,
        weiboCanOpenUrl = weiboCanOpenUrl,
        notificationsGranted = notificationsGranted,
        accessibilityEnabled = accessibilityEnabled
    )

private fun manualTestWarnings(state: UiState): List<String> = buildList {
    if (!state.batteryOptimizationIgnored) add("放行省电限制")
    if (!state.fullScreenIntentGranted) add("允许锁屏启动")
    if (!state.exactAlarmGranted) add("开启精确闹钟")
    if (state.deviceState.contains("安全锁屏")) add("先解锁手机")
}

private fun disableDailyIfBlockingPrerequisiteMissing(context: Context) {
    if (!AppPreferences.isEnabled(context)) return
    val reason = when {
        !CheckinScheduler.canScheduleExact(context) -> "精确闹钟权限未开启"
        !NotificationHelper.canNotify(context) -> "通知权限未开启"
        else -> return
    }
    AppPreferences.setEnabled(context, false)
    CheckinScheduler.cancelDailySchedule(context)
    CheckinScheduler.cancelRetry(context)
    CheckinScheduler.cancelTemporaryTest(context)
    AppPreferences.setNextDailyScheduledAt(context, null)
    AppPreferences.addLog(context, "每日签到已关闭: $reason")
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val opened = runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        if (!opened) openAppDetailsSettings(context)
    }
}

private fun openNotificationSettings(context: Context) {
    val opened = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess
    if (!opened) {
        openAppDetailsSettings(context)
    }
}

private fun openFullScreenIntentSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val opened = runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        if (!opened) openNotificationSettings(context)
    } else {
        openNotificationSettings(context)
    }
}

private fun openAppDetailsSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        AppPreferences.addLog(context, "打开系统设置失败: ${it.message}")
    }
}

private fun openBatteryOptimizationSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val requestIntent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val opened = runCatching { context.startActivity(requestIntent) }.isSuccess
    if (!opened) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            openAppDetailsSettings(context)
        }
    }
}
