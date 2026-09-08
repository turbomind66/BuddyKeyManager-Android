package cn.buddykeymanager.app.store

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ProxySettings(
    val enabled: Boolean = false,
    val host: String = "",
    val port: Int = 1080,
    val username: String = "",
    val password: String = ""
) {
    val valid: Boolean
        get() = enabled && host.trim().isNotEmpty() && port in 1..65535
    val fingerprint: String
        get() = "$enabled|$host|$port|$username|${password.length}"
}

/** 全局配置（SharedPreferences + StateFlow，等价于 iOS 的 UserDefaults / @Published） */
object Prefs {

    private const val FILE = "buddy_prefs"
    private lateinit var appCtx: Context

    private val sp get() = appCtx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    val region: MutableStateFlow<String> = MutableStateFlow("cn")
    val proxy: MutableStateFlow<ProxySettings> = MutableStateFlow(ProxySettings())

    val smsProvider: MutableStateFlow<String> = MutableStateFlow("ejiema")   // ejiema / workbuddy
    val smsToken: MutableStateFlow<String> = MutableStateFlow("")
    val smsCardKey: MutableStateFlow<String> = MutableStateFlow("")
    val smsUrl: MutableStateFlow<String> = MutableStateFlow("")
    val smsKeyword: MutableStateFlow<String> = MutableStateFlow("腾讯")
    val smsCardType: MutableStateFlow<String> = MutableStateFlow("实卡")
    val smsAutoMode: MutableStateFlow<Boolean> = MutableStateFlow(false)

    val pushBaseURL: MutableStateFlow<String> = MutableStateFlow("")
    val pushPassword: MutableStateFlow<String> = MutableStateFlow("")

    val githubToken: MutableStateFlow<String> = MutableStateFlow("")

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        region.value = sp.getString("region", "cn") ?: "cn"
        proxy.value = ProxySettings(
            enabled = sp.getBoolean("proxy_enabled", false),
            host = sp.getString("proxy_host", "") ?: "",
            port = sp.getInt("proxy_port", 1080),
            username = sp.getString("proxy_user", "") ?: "",
            password = sp.getString("proxy_pass", "") ?: ""
        )
        smsProvider.value = sp.getString("sms_provider", "ejiema") ?: "ejiema"
        smsToken.value = sp.getString("sms_token", "") ?: ""
        smsCardKey.value = sp.getString("sms_card_key", "") ?: ""
        smsUrl.value = sp.getString("sms_url", "") ?: ""
        smsKeyword.value = sp.getString("sms_keyword", "腾讯") ?: "腾讯"
        smsCardType.value = sp.getString("sms_card_type", "实卡") ?: "实卡"
        smsAutoMode.value = sp.getBoolean("sms_auto_mode", false)
        pushBaseURL.value = sp.getString("push_baseurl", "") ?: ""
        pushPassword.value = sp.getString("push_password", "") ?: ""
        githubToken.value = sp.getString("github_token", "") ?: ""
    }

    fun context(): Context = appCtx

    fun setRegion(v: String) { region.value = v; sp.edit().putString("region", v).apply() }

    fun setProxy(p: ProxySettings) {
        proxy.value = p
        sp.edit().apply {
            putBoolean("proxy_enabled", p.enabled)
            putString("proxy_host", p.host)
            putInt("proxy_port", p.port)
            putString("proxy_user", p.username)
            putString("proxy_pass", p.password)
        }.apply()
    }

    fun setSmsProvider(v: String) { smsProvider.value = v; sp.edit().putString("sms_provider", v).apply() }
    fun setSmsToken(v: String) { smsToken.value = v; sp.edit().putString("sms_token", v).apply() }
    fun setSmsCardKey(v: String) { smsCardKey.value = v; sp.edit().putString("sms_card_key", v).apply() }
    fun setSmsUrl(v: String) { smsUrl.value = v; sp.edit().putString("sms_url", v).apply() }
    fun setSmsKeyword(v: String) { smsKeyword.value = v; sp.edit().putString("sms_keyword", v).apply() }
    fun setSmsCardType(v: String) { smsCardType.value = v; sp.edit().putString("sms_card_type", v).apply() }
    fun setSmsAutoMode(v: Boolean) { smsAutoMode.value = v; sp.edit().putBoolean("sms_auto_mode", v).apply() }
    fun setPushBaseURL(v: String) { pushBaseURL.value = v; sp.edit().putString("push_baseurl", v).apply() }
    fun setPushPassword(v: String) { pushPassword.value = v; sp.edit().putString("push_password", v).apply() }
    fun setGithubToken(v: String) { githubToken.value = v; sp.edit().putString("github_token", v).apply() }
}
