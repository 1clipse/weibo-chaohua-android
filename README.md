# 微博超话签到助手 Android APK

这是一个自用 Debug APK 工程，用于备用安卓机每天 10:00 打开微博 App，进入林俊杰超话并通过无障碍服务识别/点击签到。

它不保存微博密码，不处理验证码，不做批量账号；检测到登录失效、验证码、安全验证、账号异常或访问受限时会停止并发本机通知。

## 功能

- 默认超话：林俊杰超话
- 默认时间：每天 10:00
- 手动测试签到
- 每日闹钟调度
- 开机、时间变化、时区变化后恢复调度
- 无障碍服务只监听 `com.sina.weibo`
- 最近日志
- 本机通知

## 手机准备

1. 安装微博 App，并在微博 App 内登录。
2. 备用机保持插电、联网、开机。
3. 锁屏建议设为无密码或滑动解锁。
4. 安装本 APK 后开启：
   - 通知权限
   - 无障碍权限：`微博超话签到助手`
   - 精确闹钟权限，Android 12+ 可能需要手动开启
5. 打开 App，确认每日签到开关已开启，时间为 `10:00`。

## 已构建 APK

当前 Debug APK 已输出到：

```text
D:\codex-outputs\weibo-chaohua-android\apk\weibo-chaohua-checkin-debug.apk
```

如果电脑已安装 ADB，可用 USB 调试安装：

```powershell
D:\codex-deps\weibo-chaohua-android\android-sdk\platform-tools\adb.exe install -r D:\codex-outputs\weibo-chaohua-android\apk\weibo-chaohua-checkin-debug.apk
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

## 真机测试

1. 安装 APK。
2. 授权通知、无障碍、精确闹钟。
3. 点“手动测试签到”。
4. 确认微博打开到目标超话。
5. 已签到时应发成功通知；未签到时应点击签到后发成功通知。
6. 退出微博登录后再次测试，应发失败通知并记录日志。

## 注意

这个 APK 依赖微博 App 的可见文字和按钮结构。如果微博 UI 改版，需要更新 `CheckinTextClassifier.kt` 或 `WeiboAccessibilityService.kt` 的识别/点击规则。
