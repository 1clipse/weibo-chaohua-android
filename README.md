# 微博超话签到助手 Android APK

这是一个自用 Debug APK 工程，用于备用安卓机按用户选择的每日尝试时间打开微博 App，进入配置好的超话并通过无障碍服务识别/点击签到。

它会优先避免打扰正在使用的手机；如果系统拦截后台打开微博，或设备处于安全锁屏状态，App 会记录原因并通过本机通知提示你点按继续。

它不保存微博密码，不处理验证码，不做批量账号；检测到登录失效、验证码、安全验证、账号异常或访问受限时会停止并发本机通知。

边界说明：本 APK 只在你已授权的本机和微博可见页面内辅助点击，不后台绕过登录、验证码、安全验证或平台风控。

## 运行界面

当前 App 是极简单页界面，支持白天/夜间模式。页面包含今日状态、权限状态、目标超话、滚轮式时间选择、测试按钮、最近日志和诊断复制入口。

> 说明：`docs/screenshots` 中的旧截图暂不作为验收依据。请以真机安装后的当前界面为准；最终落地前再刷新白天/夜间截图。

## 功能

- 目标超话可在 App 内配置
- 每日尝试时间可选择，默认 `10:00`；最晚可选 `22:59`，当天截止时间固定为 `23:00`
- 手动测试签到
- 2 分钟后临时定时测试，用于验证系统闹钟和空闲判断链路，不改变每日时间
- 今日结果会显示临时测试安排时间，方便确认是否已经排上
- 手动测试前显示准备状态，缺少微博 App、无障碍或可打开的超话 URL 时不会直接跳转
- 每日闹钟调度
- 开机、时间变化、时区变化后恢复调度
- 手机正在使用时延后重试，安全锁屏、系统拦截或省电限制时给出明确提示
- 45 秒内未识别到微博页面时自动超时并提示
- 进程内 watchdog + 系统闹钟双保险，减少省电/Doze 导致的超时延迟
- 签到执行窗口内使用 60 秒短时 wakelock，降低息屏后流程被系统打断的概率
- 无障碍服务只监听 `com.sina.weibo`
- 当前设备状态、下次重试时间、截止时间
- 诊断摘要：微博 App 状态、自动化窗口、最后阶段、最近识别文本
- 本地时间格式的最近日志，失败/风控日志会优先显示关键提示
- 真机验收前可重置日志和诊断状态；如果当天仍有等待空闲或人工处理后的重试任务，重置不会取消排队重试
- 时间选择器会提示将保存的时间，避免滚轮高亮值和保存值不一致
- 本机通知

## 手机准备

1. 安装微博 App，并在微博 App 内登录。
2. 手机保持插电、联网、开机。
3. 备用机建议使用无密码或滑动解锁。如果是安全锁屏，App 不承诺静默完成，会通知你解锁后继续。
4. 安装本 APK 后开启：
   - 通知权限；未开启或通知渠道被关闭时，每日自动签到开关不会保持启用
   - 无障碍权限：`微博超话签到助手`
   - 精确闹钟权限，Android 12+ 可能需要手动开启；未开启时每日自动签到开关不会保持启用
   - 省电限制：允许本应用忽略电池优化；部分手机还需要在系统管家里允许后台运行/自启动
5. 打开 App，确认每日签到开关已开启，并选择每日尝试时间。

## 小米 / HyperOS 准备

小米和 HyperOS 会额外限制后台、自启动和无障碍。真机验收前建议逐项确认：

- 应用信息页：打开 `微博超话签到助手` 的 `自启动`。
- 省电策略：选择无限制或允许后台运行。
- 无障碍：进入系统无障碍页，找到 `微博超话签到助手` 并开启。只打开“无障碍功能菜单”不等于开启本 App。
- 如果无障碍无法开启：在应用信息页右上角更多菜单里查找 `允许受限设置`，允许后再回无障碍页开启。
- 如果希望 Codex 直接帮你点手机界面：开发者选项里还需要开启 `USB 调试（安全设置）`；否则小米会拒绝 ADB 点击，只能安装、启动页面和抓诊断。
- 可用安装脚本的安装后 `Readiness summary` 或 `.\collect-device-diagnostics.ps1 -Screenshot` 导出诊断；device 文件顶部会提示无障碍、受限设置、自启动/后台、通知、精确闹钟是否可疑。

## 锁屏/待机规则

- 手机亮屏且未锁屏：认为你正在使用，暂不跳转微博，每 15 分钟重试一次。
- 等待空闲期间如果检测到息屏，会立即重新预检查，尽量不用等满 15 分钟。
- 手机息屏或非安全锁屏：允许自动打开微博执行签到；Android 10+ 会优先使用锁屏全屏通知拉起中转页，降低后台启动被系统拦截的概率。
- 安全锁屏：停止自动打开微博，发通知提醒解锁后继续。
- 到当天 23:00 仍不能执行：停止当日重试，并在通知和日志里写明真实原因。
- 如果微博或系统省电策略拦截后台启动，App 会通过进程内 watchdog 优先在 45 秒左右结束本次尝试；进程被系统杀掉时再由系统闹钟兜底。

## 已构建 APK

当前本地待验收版为 `1.1.9`，尚未发布到 GitHub Release。验收时请安装下面的本地 APK，暂时不要使用旧 Release 包：

```text
D:\codex-outputs\weibo-chaohua-android\apk\weibo-chaohua-checkin-debug.apk
```

本地待验收版 SHA256：

```text
194DF6D59FE50E3E0154EA9B434ECFD8ABA0856408363283DB2739C10E0D7F48
```

仓库内也包含当前 Debug APK 副本：

```text
releases\weibo-chaohua-checkin-debug.apk
```

如果电脑已连接安卓机并开启 USB 调试，可用脚本安装：

```powershell
.\install-debug-apk.ps1
```

安装后自动打开 App：

```powershell
.\install-debug-apk.ps1 -Launch
```

安装后尝试通过 ADB 授予通知权限：

```powershell
.\install-debug-apk.ps1 -GrantNotifications -Launch
```

安装后打开 App，并跳到无障碍设置页：

```powershell
.\install-debug-apk.ps1 -GrantNotifications -Launch -OpenAccessibilitySettings
```

需要检查精确闹钟权限时：

```powershell
.\install-debug-apk.ps1 -OpenExactAlarmSettings
```

需要检查应用权限或后台限制时：

```powershell
.\install-debug-apk.ps1 -OpenAppSettings
```

需要打开电池优化设置时：

```powershell
.\install-debug-apk.ps1 -OpenBatterySettings
```

只检查 ADB 和 APK 路径，不安装：

```powershell
.\install-debug-apk.ps1 -CheckOnly
```

多台设备连接时指定序列号：

```powershell
.\install-debug-apk.ps1 -Serial <device-serial>
```

脚本会优先使用 D 盘依赖目录里的 ADB，也会尝试 `ANDROID_HOME`、`ANDROID_SDK_ROOT` 和系统 `PATH`。如果脚本提示 `No Android device found`，请确认手机已开启 USB 调试，并在手机上点按允许本电脑调试。

如果脚本提示已安装 APK 签名不一致，请先在手机上卸载旧版本，再安装当前 Debug APK。多台设备同时连接时，请先运行 `adb devices` 查看序列号，然后使用 `-Serial <device-serial>`。

也可以直接使用 ADB 原始命令：

```powershell
D:\codex-deps\weibo-chaohua-android\android-sdk\platform-tools\adb.exe install -r D:\codex-outputs\weibo-chaohua-android\apk\weibo-chaohua-checkin-debug.apk
```

## 导出诊断

手机已连接 USB 调试时，可从电脑导出 App 本地诊断偏好：

```powershell
.\collect-device-diagnostics.ps1
```

导出前请先打开 App 至少一次，否则本地偏好文件可能还不存在。

同时保存当前手机截图：

```powershell
.\collect-device-diagnostics.ps1 -Screenshot
```

如果偏好文件暂时读不到，脚本仍会导出设备状态和截图，并在 device 文件里写入 `warnings`。常见原因是未安装当前 Debug APK、安装了非 Debug 包，或安装后还没有打开过 App。

输出目录：

```text
D:\codex-outputs\weibo-chaohua-android\diagnostics
```

## 构建

推荐构建命令。该脚本会固定使用 D 盘依赖目录，并把 APK 复制到 D 盘：

```powershell
.\build-debug-apk.ps1
```

默认目录：

```text
D:\codex-deps\weibo-chaohua-android
D:\codex-outputs\weibo-chaohua-android\apk\weibo-chaohua-checkin-debug.apk
```

说明：脚本会把 JDK、Gradle 缓存、Android SDK 和最终交付 APK 放在 D 盘。Gradle 的临时中间产物仍会出现在当前项目目录的 `app\build` 下。

标准 Gradle 构建命令：

```powershell
.\gradlew.bat assembleDebug
```

APK 输出路径：

```text
app\build\outputs\apk\debug\app-debug.apk
```

如果本机没有 Android SDK，请安装 Android Studio 或 Android command-line tools，并安装至少：

```powershell
sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
```

最终提交或发布前，运行本地交付自检，检查脚本语法、安装/诊断脚本基础状态，以及 D 盘 APK、仓库 APK 副本和 README SHA256 是否一致：

```powershell
.\validate-local-delivery.ps1
```

需要同时重新构建 APK 时：

```powershell
.\validate-local-delivery.ps1 -Build
```

只检查 APK 哈希一致性时：

```powershell
.\verify-local-delivery.ps1
```

## 真机测试

1. 安装或覆盖安装本地待验收 APK，推荐运行 `.\install-debug-apk.ps1`。
2. 授权通知、无障碍、精确闹钟。安装脚本可用 `-OpenAccessibilitySettings` 和 `-OpenExactAlarmSettings` 打开对应系统页。
3. 打开“省电限制”设置，允许忽略电池优化；必要时在系统管家里允许后台运行。安装脚本可用 `-OpenAppSettings` 或 `-OpenBatterySettings` 辅助打开系统页。
4. 小米 / HyperOS 设备先完成上面的专门准备，尤其是无障碍、自启动和省电策略。
5. 运行 `.\collect-device-diagnostics.ps1 -Screenshot`，确认 `Readiness summary` 没有无障碍、通知、精确闹钟的阻断项。
6. 点“每日尝试时间”，确认滚轮式时间选择器能保存新时间。
7. 查看“测试”卡片，确认没有“还需处理”后再点“手动测试签到”。
8. 确认微博打开到目标超话。
9. 已签到时应发成功通知；未签到时应点击签到后发成功通知。
10. 点“2 分钟后定时测试”，确认“今日结果”里出现临时测试时间；保持手机亮屏使用中，确认到点不跳转微博且状态为等待空闲。
11. 随后息屏，确认会尽快重新预检查。
12. 锁屏、安全锁屏、正在使用手机时分别测试一次，确认状态、日志和通知能说明原因。
13. 退出微博登录后再次测试，应发失败通知并记录日志。
14. 每轮验收前、且当前没有“正在尝试签到”时，可点“重置诊断”，确认旧日志、最近阶段和最近识别已清空；如果状态仍在等待空闲或已安排重试，重置不会取消当天重试。
15. 任一场景失败时，点“复制诊断”，把复制出的文本连同截图一起保存；也可以运行 `.\collect-device-diagnostics.ps1 -Screenshot` 从电脑导出诊断。

## 注意

这个 APK 依赖微博 App 的可见文字和按钮结构。如果微博 UI 改版，需要更新 `CheckinTextClassifier.kt` 或 `WeiboAccessibilityService.kt` 的识别/点击规则。
