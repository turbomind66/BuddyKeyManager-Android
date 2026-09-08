# BuddyKeyManager-Android

> iOS SwiftUI 项目 [BuddyKeyManager](https://github.com/Admin6016/BuddyKeyManager) 的 **Android 移植版**（Kotlin + Jetpack Compose）。

一个面向多账号管理场景的本地工具，覆盖 **OAuth 授权 → 无痕浏览器登录 → 接码取号取码 → 凭证测活/标注/筛选 → 推送到 Buddy2API** 的完整工作流。

---

## 功能

| 模块 | 说明 |
|------|------|
| 授权会话 | 创建授权链接 → 内置无痕 WebView 登录 → 凭证自动入库（支持中国区 / 国际区切换） |
| 接码 | ejiema Token 或 workbuddy 卡密二选一；取号 / 取码 / 自动填号 / 自动填码 / 手动/自动换号 / 全自动换号模式 |
| 凭证管理 | 单条测活、批量测活、查余额套餐、标注、筛选、复制 token、导出 JSON、推送到 Buddy2API 服务器 |
| 日志 | 完整操作流水，全局崩溃堆栈自动并入 |
| 设置 | 接码平台、推送服务、代理等配置 |

## 架构

```
app/src/main/java/cn/buddykeymanager/app/
├── BkmApp.kt              # Application：全局崩溃捕获
├── MainActivity.kt        # Compose 入口
├── data/Models.kt         # 全部数据模型（Session/Credential/Sms…）
├── net/                   # 网络层
│   ├── Http.kt            # OkHttp 客户端 + 协程桥接
│   ├── BuddyAPI.kt        # 授权/刷新/凭证增删改查
│   ├── SmsAPI.kt          # ejiema / svipxx 接码 API
│   └── PushAPI.kt         # 推送到 Buddy2API
├── store/                 # 全局状态
│   ├── Prefs.kt           # SharedPreferences 封装
│   ├── CredentialStore.kt # 授权会话 + 凭证轮询/测活
│   └── SmsStore.kt        # 接码状态机
├── web/BuddyWebView.kt    # 无痕 WebView + 登录自动填号
├── theme/Theme.kt         # Compose 主题
├── ui/                    # 界面
│   ├── App.kt             # Tab 容器
│   ├── SessionsScreen.kt  # 授权会话页
│   ├── CredentialsScreen.kt # 凭证页
│   ├── LogScreen.kt       # 日志页
│   ├── SettingsScreen.kt  # 设置页
│   └── Components.kt      # 公共组件
└── util/Notify.kt         # 通知 + 锁屏
```

## 技术栈

- **语言**：Kotlin 1.9.24
- **UI**：Jetpack Compose（BOM 2024.06.00） + Material 3
- **网络**：OkHttp 4.12 + 协程
- **构建**：Gradle 8.7 + AGP 8.5
- **最低 SDK**：26（Android 8.0）/ 目标 SDK 34

## 编译

```bash
# 1. 准备工具链
# - JDK 17
# - Android SDK: platform-tools, platforms;android-34, build-tools;34.0.0
# - Gradle 8.7+ 或直接使用项目内的 Gradle wrapper

# 2. 在 local.properties 写入 SDK 路径
echo "sdk.dir=C\\:\\\\Users\\\\<you>\\\\AppData\\\\Local\\\\Android\\\\Sdk" > local.properties

# 3. 构建 APK
./gradlew assembleDebug          # Debug APK
./gradlew assembleRelease        # Release APK（debug 签名）
```

> ⚠️ 如果项目放在被「百度网盘」等同步软件监管的盘符下，编译前请先排除 `build/` 和 `.gradle/`，否则中间产物会被改名为 `*.baiduyun.uploading.cfg` 致 AAPT2 链接失败。

## 从 iOS 移植的关键改动

| iOS (SwiftUI) | Android (Compose) |
|---------------|-------------------|
| `Codable` 模型 | `data class` + `org.json.JSONObject` |
| `URLSession` | OkHttp + 协程 |
| `Keychain` | `SharedPreferences` (明文) / `EncryptedSharedPreferences` 可选 |
| `WebKit.WKWebView` | `android.webkit.WebView` + 无痕式配置（无 cookie 持久化） |
| `@StateObject` 单例 | `object` 单例 + `MutableStateFlow` |
| `NotificationCenter` | `NotificationCompat` + `BroadcastReceiver` |
| `UserDefaults` | `SharedPreferences` |

## 发行版

请到 [GitHub Releases](https://github.com/turbomind66/BuddyKeyManager-Android/releases) 下载已编译的 APK（含 `app-release.apk` 与各版本增量更新）。

## License

仅供个人学习与工作流辅助使用。**禁止用于任何违法违规活动。**
