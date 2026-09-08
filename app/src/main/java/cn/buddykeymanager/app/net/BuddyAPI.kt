package cn.buddykeymanager.app.net

import android.net.Uri
import cn.buddykeymanager.app.data.BuddyCredential
import cn.buddykeymanager.app.data.TokenData
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ApiException(message: String) : Exception(message)

data class ResourcePackage(
    val packageName: String,
    val capacitySize: Long,
    val capacityRemain: Long,
    val capacityUsed: Long,
    val cycleSize: Long,
    val cycleRemain: Long,
    val cycleUsed: Long
)

/**
 * WorkBuddy / CodeBuddy OAuth 网络层
 * 协议：POST /v2/plugin/auth/state → 浏览器登录 → GET /v2/plugin/auth/token 轮询
 *       → 从 access_token 的 JWT 提取 uid / nickname
 */
class BuddyAPI(regionKey: String = "cn") {

    enum class Region(val key: String, val label: String, val upstream: String, val domain: String, val origin: String) {
        CN("cn", "中国区", "https://copilot.tencent.com", "www.codebuddy.cn", "https://www.codebuddy.cn"),
        GLOBAL("global", "国际区", "https://www.codebuddy.ai", "www.codebuddy.ai", "https://www.workbuddy.ai");

        companion object {
            fun from(key: String) = if (key == "global") GLOBAL else CN
        }
    }

    var region: Region = Region.from(regionKey)
        private set

    private val jsonMedia = "application/json; charset=utf-8".toMediaTypeOrNull()

    // MARK: - 请求头

    private fun oauthHeaders(): Map<String, String> {
        val rid = uuidHex()
        return mapOf(
            "Accept" to "application/json, text/plain, */*",
            "Content-Type" to "application/json",
            "Cache-Control" to "no-cache",
            "Pragma" to "no-cache",
            "X-Requested-With" to "XMLHttpRequest",
            "X-Domain" to "www.codebuddy.ai",
            "X-No-Authorization" to "true",
            "X-No-User-Id" to "true",
            "X-No-Enterprise-Id" to "true",
            "X-No-Department-Info" to "true",
            "X-Product" to "SaaS",
            "User-Agent" to userAgent(),
            "X-Request-ID" to rid,
            "X-B3-TraceId" to rid,
            "X-B3-SpanId" to randHex(8),
            "X-B3-Sampled" to "1"
        )
    }

    private fun refreshHeaders(cred: BuddyCredential): Map<String, String> {
        val rid = uuidHex()
        val h = linkedMapOf(
            "Content-Type" to "application/json",
            "Accept" to "application/json, text/plain, */*",
            "Cache-Control" to "no-cache",
            "Pragma" to "no-cache",
            "X-Requested-With" to "XMLHttpRequest",
            "Origin" to region.origin,
            "Referer" to region.origin + "/",
            "X-Product" to "SaaS",
            "User-Agent" to userAgent(),
            "X-Request-ID" to rid,
            "X-B3-TraceId" to rid,
            "X-B3-SpanId" to randHex(8),
            "X-B3-Sampled" to "1",
            "X-Auth-Refresh-Source" to "plugin"
        )
        if (cred.auth.accessToken.isNotEmpty()) h["Authorization"] = "Bearer ${cred.auth.accessToken}"
        h["X-Refresh-Token"] = cred.auth.refreshToken
        if (cred.account.uid.isNotEmpty()) h["X-User-Id"] = cred.account.uid else h["X-No-User-Id"] = "1"
        if (cred.account.enterpriseId.isNotEmpty()) h["X-Enterprise-Id"] = cred.account.enterpriseId
        else h["X-No-Enterprise-Id"] = "1"
        if (cred.auth.domain.isNotEmpty()) h["X-Domain"] = cred.auth.domain else h["X-No-Department-Info"] = "1"
        return h
    }

    private fun commonBillingHeaders(cred: BuddyCredential): MutableMap<String, String> {
        val rid = uuidHex()
        val h = linkedMapOf(
            "Content-Type" to "application/json",
            "Accept" to "application/json, text/plain, */*",
            "Cache-Control" to "no-cache",
            "Pragma" to "no-cache",
            "X-Requested-With" to "XMLHttpRequest",
            "Origin" to region.origin,
            "Referer" to region.origin + "/",
            "User-Agent" to userAgent(),
            "X-Request-ID" to rid,
            "X-B3-TraceId" to rid,
            "X-B3-SpanId" to randHex(8),
            "X-B3-Sampled" to "1"
        )
        if (cred.auth.accessToken.isNotEmpty()) h["Authorization"] = "Bearer ${cred.auth.accessToken}"
        else h["X-No-Authorization"] = "1"
        if (cred.account.uid.isNotEmpty()) h["X-User-Id"] = cred.account.uid else h["X-No-User-Id"] = "1"
        if (cred.account.enterpriseId.isNotEmpty()) {
            h["X-Enterprise-Id"] = cred.account.enterpriseId
            h["X-Tenant-Id"] = cred.account.enterpriseId
        } else {
            h["X-No-Enterprise-Id"] = "1"
        }
        if (cred.auth.domain.isNotEmpty()) h["X-Domain"] = cred.auth.domain else h["X-No-Department-Info"] = "1"
        return h
    }

    private fun chatHeaders(cred: BuddyCredential): MutableMap<String, String> {
        val rid = uuidHex()
        val span = randHex(8)
        val h = commonBillingHeaders(cred)
        h["X-Product"] = "SaaS"
        h["b3"] = "$rid-$span-1-"
        h["X-IDE-Type"] = "CLI"
        h["X-IDE-Name"] = "CLI"
        h["X-IDE-Version"] = "2.63.2"
        h["x-codebuddy-request"] = "1"
        h["X-Agent-Intent"] = "craft"
        h["X-Conversation-ID"] = uuidHex()
        h["X-Conversation-Request-ID"] = rid
        h["X-Conversation-Message-ID"] = uuidHex()
        h["x-stainless-arch"] = "x64"
        h["x-stainless-lang"] = "js"
        h["x-stainless-os"] = "Linux"
        h["x-stainless-package-version"] = "2.63.2"
        h["x-stainless-retry-count"] = "0"
        h["x-stainless-runtime"] = "node"
        h["x-stainless-runtime-version"] = "20.11.0"
        return h
    }

    // MARK: - 1. 创建授权链接

    suspend fun createAuthSession(platform: String = "CLI"): Pair<String, String> {
        val nonce = randHex(8)
        val url =
            "${region.upstream}/v2/plugin/auth/state?platform=${Uri.encode(platform)}&nonce=$nonce"
        val body = "{\"nonce\":\"$nonce\"}".toRequestBody(jsonMedia)
        val req = Request.Builder().url(url).post(body).withHeaders(oauthHeaders()).build()
        val raw = Http.client().newCall(req).awaitResponse().use { it.body?.string() ?: "" }
        val env = decodeEnvelope(raw)
        val inner = env.innerData ?: throw ApiException("响应解析失败: ${raw.take(200)}")
        val authUrl = inner.optString("authUrl", "")
        val state = inner.optString("state", "")
        if (authUrl.isEmpty()) throw ApiException("响应缺少 authUrl")
        if (state.isEmpty()) throw ApiException("响应缺少 state")
        return authUrl to state
    }

    // MARK: - 2. 轮询 token（nil = 仍在等待登录，code 11217）

    suspend fun pollToken(state: String): TokenData? {
        val url = "${region.upstream}/v2/plugin/auth/token?state=${Uri.encode(state)}"
        val req = Request.Builder().url(url).get().withHeaders(oauthHeaders()).build()
        val raw = Http.client().newCall(req).awaitResponse().use { it.body?.string() ?: "" }
        val env = decodeEnvelope(raw)
        val inner = env.innerData
        if (inner == null) {
            if (env.code == 11217) return null
            throw ApiException("响应解析失败: ${raw.take(200)}")
        }
        val accessToken = inner.optString("accessToken", "")
        if (accessToken.isEmpty()) throw ApiException("响应缺少 accessToken")
        return TokenData(
            accessToken = accessToken,
            refreshToken = inner.optString("refreshToken", ""),
            expiresIn = inner.optLong("expiresIn", 0L),
            domain = inner.optString("domain", "").let { if (it.isEmpty()) null else it }
        )
    }

    // MARK: - 3. 刷新 token

    suspend fun refresh(cred: BuddyCredential): TokenData {
        val url = "${region.upstream}/v2/plugin/auth/token/refresh"
        val body = "{}".toRequestBody(jsonMedia)
        val req = Request.Builder().url(url).post(body).withHeaders(refreshHeaders(cred)).build()
        val raw = Http.client().newCall(req).awaitResponse().use { it.body?.string() ?: "" }
        val env = decodeEnvelope(raw)
        val inner = env.innerData
        val accessToken = inner?.optString("accessToken", "") ?: ""
        if (accessToken.isEmpty()) throw ApiException("刷新失败：响应缺少 accessToken")
        return TokenData(
            accessToken = accessToken,
            refreshToken = inner?.optString("refreshToken", "") ?: "",
            expiresIn = inner?.optLong("expiresIn", 0L) ?: 0L,
            domain = inner?.optString("domain", "")?.let { if (it.isEmpty()) null else it }
        )
    }

    // MARK: - 4. 测活（最小 chat 请求）

    suspend fun ping(cred: BuddyCredential) {
        val url = "${region.upstream}/v2/chat/completions"
        val payload = JSONObject().apply {
            put("model", "auto")
            put("stream", true)
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", "You are a helpful assistant."))
                put(JSONObject().put("role", "user").put("content", "你好"))
            })
            put("stream_options", JSONObject().put("include_usage", true))
        }
        val body = payload.toString().toRequestBody(jsonMedia)
        val req = Request.Builder().url(url).post(body).withHeaders(chatHeaders(cred)).build()
        val resp = Http.client().newCall(req).awaitResponse()
        val code = resp.code
        val text = resp.use { r -> r.body?.string()?.take(400) ?: "" }
        when (code) {
            401 -> throw ApiException("HTTP 401: $text")
            403 -> throw ApiException("HTTP 403: 安全审核拦截（凭证可能有效）：$text")
            else -> if (code >= 400) throw ApiException("HTTP $code: $text")
        }
    }

    // MARK: - 5. 每日签到

    suspend fun dailyCheckin(cred: BuddyCredential): Boolean {
        val url = "${region.upstream}/v2/billing/meter/daily-checkin"
        val h = commonBillingHeaders(cred)
        h["X-Product"] = "SaaS"
        val req = Request.Builder().url(url).post("{}".toRequestBody(jsonMedia)).withHeaders(h).build()
        val raw = Http.client().newCall(req).awaitResponse().use { it.body?.string() ?: "" }
        val env = decodeEnvelope(raw)
        val c = env.code
        if (c != null && c != 0) throw ApiException("code=$c msg=${env.msg ?: ""}")
        return true
    }

    // MARK: - 6. 余额查询

    suspend fun userResource(cred: BuddyCredential): List<ResourcePackage> {
        val url = "${region.upstream}/v2/billing/meter/get-user-resource"
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
        val now = Date()
        val end = Date(now.time + 365L * 101 * 24 * 3600 * 1000)
        val payload = JSONObject().apply {
            put("PageNumber", 1)
            put("PageSize", 100)
            put("ProductCode", "p_tcaca")
            put("Status", org.json.JSONArray().put(0).put(3))
            put("PackageEndTimeRangeBegin", fmt.format(now))
            put("PackageEndTimeRangeEnd", fmt.format(end))
        }
        val req = Request.Builder().url(url)
            .post(payload.toString().toRequestBody(jsonMedia))
            .withHeaders(commonBillingHeaders(cred))
            .build()
        val raw = Http.client().newCall(req).awaitResponse().use { it.body?.string() ?: "" }

        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val data = root.optJSONObject("data") ?: return emptyList()
        val resp = data.optJSONObject("Response") ?: return emptyList()
        val rdata = resp.optJSONObject("Data") ?: return emptyList()
        val accounts = rdata.optJSONArray("Accounts") ?: return emptyList()
        val out = mutableListOf<ResourcePackage>()
        for (i in 0 until accounts.length()) {
            val a = accounts.optJSONObject(i) ?: continue
            out.add(
                ResourcePackage(
                    packageName = a.optString("PackageName", "套餐"),
                    capacitySize = a.optLong("CapacitySize", 0L),
                    capacityRemain = a.optLong("CapacityRemain", 0L),
                    capacityUsed = a.optLong("CapacityUsed", 0L),
                    cycleSize = a.optLong("CycleCapacitySize", 0L),
                    cycleRemain = a.optLong("CycleCapacityRemain", 0L),
                    cycleUsed = a.optLong("CycleCapacityUsed", 0L)
                )
            )
        }
        return out
    }

    suspend fun totalResource(cred: BuddyCredential): Long {
        var total = 0L
        for (p in userResource(cred)) total += kotlin.math.max(remainOf(p), 0L)
        return total
    }

    // MARK: - 工具

    private fun remainOf(p: ResourcePackage): Long = when {
        p.cycleSize > 0 -> p.cycleRemain
        p.cycleRemain > 0 || p.cycleUsed > 0 -> p.cycleRemain
        else -> p.capacityRemain
    }

    private class Envelope(val code: Int?, val msg: String?, val data: JSONObject?) {
        val innerData: JSONObject?
            get() {
                var cur = data ?: return null
                while (true) {
                    val next = cur.optJSONObject("data") ?: break
                    if (cur.length() <= 1) cur = next else break
                }
                return cur
            }
    }

    private fun decodeEnvelope(raw: String): Envelope {
        val obj = runCatching { JSONObject(raw) }.getOrNull()
            ?: throw ApiException("响应解析失败: ${raw.take(200)}")
        val code = if (obj.has("code")) obj.optInt("code", 0) else null
        val msg = obj.optString("msg", "").let { if (it.isEmpty()) null else it }
        if (code != null && code != 0 && code != 11217) throw ApiException("code=$code msg=${msg ?: ""}")
        return Envelope(code, msg, obj.optJSONObject("data"))
    }

    companion object {
        fun userAgent(): String = "CLI/2.63.2 CodeBuddy/2.63.2"

        private val secureRandom = SecureRandom()

        fun uuidHex(): String {
            val b = ByteArray(16)
            secureRandom.nextBytes(b)
            b[6] = ((b[6].toInt() and 0x0f) or 0x40).toByte()
            b[8] = ((b[8].toInt() and 0x3f) or 0x80).toByte()
            return b.joinToString("") { "%02x".format(it) }
        }

        fun randHex(n: Int): String {
            val b = ByteArray(n)
            secureRandom.nextBytes(b)
            return b.joinToString("") { "%02x".format(it) }
        }
    }
}
