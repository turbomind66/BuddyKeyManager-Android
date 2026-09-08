package cn.buddykeymanager.app.store

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PowerManager
import android.webkit.WebView
import cn.buddykeymanager.app.net.SmsAPI
import cn.buddykeymanager.app.net.SvipxxSmsAPI
import cn.buddykeymanager.app.util.Notify
import cn.buddykeymanager.app.web.LoginAutofill
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 接码逻辑（ejiema Token / workbuddy 卡密二选一），对应 iOS 的 SmsStore */
object SmsStore {

    const val POLL_TIMEOUT = 180
    const val AUTO_RETRY_SECONDS = 15

    val phone = MutableStateFlow("")
    val code = MutableStateFlow("")
    val smsContent = MutableStateFlow("")
    val statusText = MutableStateFlow("未取号")
    val isPolling = MutableStateFlow(false)
    val balance = MutableStateFlow("—")
    val balanceUpdatedAt = MutableStateFlow("")
    val errorMessage = MutableStateFlow<String?>(null)
    val phoneGetTime = MutableStateFlow(0L)

    @Volatile
    var loginWebView: WebView? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isAutoRephoning = false

    private fun isSvipxx() = Prefs.smsProvider.value == "workbuddy"

    fun hasToken(): Boolean =
        if (isSvipxx()) Prefs.smsCardKey.value.trim().isNotEmpty()
        else Prefs.smsToken.value.trim().isNotEmpty()

    private fun activeCredential(): String =
        if (isSvipxx()) Prefs.smsCardKey.value.trim() else Prefs.smsToken.value.trim()

    private fun appendLog(msg: String) = CredentialStore.appendLog(msg)

    private fun copy(text: String) {
        runCatching {
            val cm = Prefs.context().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("bkm", text))
        }
    }

    // MARK: - 余额

    fun queryBalance() {
        scope.launch {
            if (!hasToken()) return@launch
            runCatching { SmsAPI.leftAmount(Prefs.smsToken.value) }
                .onSuccess {
                    balance.value = it
                    balanceUpdatedAt.value = SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date())
                }
                .onFailure { balance.value = "查询失败" }
        }
    }

    // MARK: - 取号

    fun getPhone(retryCount: Int = 0) {
        scope.launch { getPhoneInternal(retryCount) }
    }

    private suspend fun getPhoneInternal(retryCount: Int) {
        if (!hasToken()) {
            errorMessage.value = "未设置接码凭据（Token 或卡密），请到「设置」页填写"
            return
        }
        if (!isAutoRephoning) stopPolling()
        statusText.value = if (retryCount > 0) "取号中…（第 $retryCount 次重试）" else "取号中…"
        val oldPhone = phone.value
        phone.value = ""
        code.value = ""
        smsContent.value = ""
        try {
            val num = if (isSvipxx()) getSvipxxPhone()
            else SmsAPI.getPhone(Prefs.smsToken.value, Prefs.smsKeyword.value, Prefs.smsCardType.value)
            val digitsOnly = num.filter { it.isDigit() }.take(11)
            if (digitsOnly.length < 11) throw Exception("取号返回异常: ${num.take(50)}")
            phone.value = digitsOnly
            phoneGetTime.value = System.currentTimeMillis()
            statusText.value = "✅ $digitsOnly（已复制）"
            appendLog("📞 取号成功: $digitsOnly")
            copy(digitsOnly)
            LoginAutofill.fillPhone(loginWebView, digitsOnly)
            startPolling()
        } catch (e: Exception) {
            if (oldPhone.isNotEmpty()) runCatching { releaseCurrentPhone(oldPhone) }
            statusText.value = "取号失败"
            errorMessage.value = e.message
            appendLog("❌ 取号失败: ${e.message}")
            if (Prefs.smsAutoMode.value && retryCount < 20) {
                appendLog("⏳ 取号失败，5s 后重试（第 ${retryCount + 1} 次）: ${e.message}")
                delay(5_000)
                getPhoneInternal(retryCount + 1)
            } else if (Prefs.smsAutoMode.value) {
                statusText.value = "取号失败（已达最大重试次数）"
            }
        }
    }

    private suspend fun getSvipxxPhone(): String {
        val resp = SvipxxSmsAPI.getPhone(activeCredential())
        val ph = resp.optJSONObject("data")?.optString("phone") ?: ""
        if (ph.isEmpty()) throw Exception("svipxx 取号失败: ${resp.optString("msg", resp.toString().take(80))}")
        return ph
    }

    private suspend fun releaseCurrentPhone(ph: String) {
        if (isSvipxx()) runCatching { SvipxxSmsAPI.changePhone(activeCredential()) }
        else runCatching { SmsAPI.release(Prefs.smsToken.value, ph) }
    }

    // MARK: - 轮询取码（每 5 秒）

    private fun startPolling() {
        stopPolling()
        isPolling.value = true
        acquireWakeLock()
        val timeout = if (Prefs.smsAutoMode.value) AUTO_RETRY_SECONDS else POLL_TIMEOUT
        statusText.value = if (Prefs.smsAutoMode.value) "🤖 全自动模式：${timeout}s 无短信自动换号…"
        else "取号成功，自动等待验证码…"
        appendLog(if (Prefs.smsAutoMode.value) "🔁 开始轮询（全自动模式，${timeout}s 无短信自动换号）"
        else "🔁 开始轮询取码（${timeout}s 超时自动换号）")
        job = scope.launch {
            var elapsed = 0
            while (isActive) {
                delay(5_000)
                elapsed += 5
                if (shouldStopForSession()) { stopForSession(); return@launch }
                if (elapsed >= timeout) { autoReleaseAndRephone(); return@launch }
                pollOnce()
            }
        }
    }

    private fun shouldStopForSession(): Boolean {
        val list = CredentialStore.sessions.value
        val done = list.any { it.status == cn.buddykeymanager.app.data.SessionStatus.COMPLETED || it.status == cn.buddykeymanager.app.data.SessionStatus.FAILED }
        val active = list.any { it.status == cn.buddykeymanager.app.data.SessionStatus.PENDING || it.status == cn.buddykeymanager.app.data.SessionStatus.POLLING }
        return done && !active
    }

    private suspend fun stopForSession() {
        appendLog("⏹ 授权会话已结束，停止取码（释放 ${phone.value}）")
        if (phone.value.isNotEmpty()) releaseCurrentPhone(phone.value)
        phone.value = ""
        code.value = ""
        smsContent.value = ""
        statusText.value = "会话已结束，停止取码"
        stopPolling()
    }

    private suspend fun pollOnce() {
        val ph = phone.value
        if (ph.isEmpty()) return
        runCatching {
            val msg = if (isSvipxx()) pollSvipxxSms()
            else SmsAPI.getMsg(Prefs.smsToken.value, ph, Prefs.smsKeyword.value)
            if (msg.isNullOrEmpty()) return
            smsContent.value = msg
            if (isBlocked(msg)) {
                if (Prefs.smsAutoMode.value) { autoReleaseAndRephone(); return }
                isPolling.value = false
                statusText.value = "🚫 短信被屏蔽，请换号重试"
                appendLog("🚫 短信被屏蔽: $ph（${msg.take(60)}）")
                code.value = ""
                stopPolling()
                return
            }
            val extracted = SmsAPI.extractCode(msg) ?: ""
            code.value = extracted
            isPolling.value = false
            statusText.value = if (extracted.isEmpty()) "✅ 已收到短信" else "✅ 验证码: $extracted（已复制）"
            appendLog(if (extracted.isEmpty()) "📩 收到短信（无验证码）" else "📩 收到验证码: $extracted（已复制并自动填入）")
            if (extracted.isNotEmpty()) {
                copy(extracted)
                LoginAutofill.fillCode(loginWebView, extracted)
                Notify.send(Prefs.context(), "📩 收到验证码", "$ph 的验证码: $extracted（已自动填入）")
            }
            stopPolling()
        }
    }

    private suspend fun pollSvipxxSms(): String? {
        val card = activeCredential()
        runCatching { SvipxxSmsAPI.getSms(card) }.getOrNull()?.let {
            SvipxxSmsAPI.extractSms(it)?.let { s -> return s }
        }
        runCatching { SvipxxSmsAPI.status(card) }.getOrNull()?.let {
            SvipxxSmsAPI.extractSms(it)?.let { s -> return s }
        }
        return null
    }

    private fun isBlocked(msg: String) = msg.contains("屏蔽") || msg.contains("被拦截")

    private suspend fun autoReleaseAndRephone() {
        isAutoRephoning = true
        try {
            if (Prefs.smsAutoMode.value) {
                statusText.value = "🤖 ${AUTO_RETRY_SECONDS}s 无短信，自动换号…"
                appendLog("🔄 全自动换号（${AUTO_RETRY_SECONDS}s 无短信）：刷新页面+释放+${phone.value}")
                loginWebView?.post { loginWebView?.reload() }
                delay(2_500)
            } else {
                statusText.value = "⏱ ${POLL_TIMEOUT}s 未收到，自动换号…"
                appendLog("🔄 超时自动换号（${POLL_TIMEOUT}s 无短信）")
            }
            if (phone.value.isNotEmpty()) releaseCurrentPhone(phone.value)
            getPhoneInternal(0)
        } finally {
            isAutoRephoning = false
        }
    }

    // MARK: - 手动换号 / 释放

    fun rephone() {
        scope.launch {
            loginWebView?.post {
                loginWebView?.reload()
                statusText.value = "刷新登录页…"
            }
            appendLog("🔄 手动换号：刷新登录页 + 释放 ${phone.value}")
            delay(2_500)
            if (phone.value.isNotEmpty()) releaseCurrentPhone(phone.value)
            getPhoneInternal(0)
        }
    }

    fun releasePhone() {
        scope.launch {
            val ph = phone.value
            if (ph.isEmpty()) return@launch
            stopPolling()
            runCatching {
                if (isSvipxx()) {
                    val newPhone = SvipxxSmsAPI.changePhone(activeCredential())
                        .optJSONObject("data")?.optString("phone") ?: ""
                    statusText.value = "已换号: $newPhone"
                    appendLog("♻️ svipxx 已换号: $ph → $newPhone")
                } else {
                    val r = SmsAPI.release(Prefs.smsToken.value, ph)
                    statusText.value = "已释放 $ph：$r"
                    appendLog("♻️ 已释放号码: $ph")
                }
            }.onFailure {
                statusText.value = "释放失败"
                appendLog("❌ 释放失败: $ph — ${it.message}")
                errorMessage.value = it.message
            }
            phone.value = ""
            code.value = ""
            smsContent.value = ""
            phoneGetTime.value = 0L
            isPolling.value = false
        }
    }

    // MARK: - workbuddy 网址解析

    fun parseUrlAndRun() {
        scope.launch {
            val url = Prefs.smsUrl.value.trim()
            if (url.isEmpty()) { errorMessage.value = "请输入接码网址"; return@launch }
            val keyParam = url.substringAfterLast('?', "")
                .split('&')
                .firstOrNull { it.startsWith("key=") }
            if (keyParam != null) {
                val k = keyParam.removePrefix("key=")
                if (k.isNotEmpty()) {
                    Prefs.setSmsCardKey(k)
                    appendLog("🔑 已从网址提取卡密")
                }
            }
            if (Prefs.smsCardKey.value.trim().isEmpty()) {
                errorMessage.value = "网址中未找到卡密（?key=）"
                return@launch
            }
            getPhoneInternal(0)
        }
    }

    // MARK: - 停止

    fun stopPolling() {
        job?.cancel()
        job = null
        isPolling.value = false
        releaseWakeLock()
    }

    private fun acquireWakeLock() {
        runCatching {
            if (wakeLock == null) {
                val pm = Prefs.context().getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BuddyKeyManager:SmsPoll")
                wakeLock?.setReferenceCounted(false)
            }
            wakeLock?.acquire(30 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.let { if (it.isHeld) it.release() } }
    }
}
