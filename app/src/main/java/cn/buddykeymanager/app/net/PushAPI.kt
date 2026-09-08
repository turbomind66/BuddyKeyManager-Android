package cn.buddykeymanager.app.net

import cn.buddykeymanager.app.data.BuddyCredential
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * 推送凭证到 Buddy2API 服务器
 * POST /admin/login          {password} → Set-Cookie b2a_session
 * POST /admin/account/import {access_token, refresh_token, ...} → {ok, uid, nickname, domain}
 */
class PushAPI(baseURLInput: String) {

    private val baseURL: String = run {
        var u = baseURLInput.trim()
        if (u.endsWith("/")) u = u.dropLast(1)
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
        u
    }

    private val jsonMedia = "application/json; charset=utf-8".toMediaTypeOrNull()
    private var cookies: String = ""

    suspend fun login(password: String) {
        val url = baseURL + "/admin/login"
        val payload = JSONObject().put("password", password).toString()
        val req = Request.Builder().url(url)
            .post(payload.toRequestBody(jsonMedia))
            .addHeader("Content-Type", "application/json")
            .build()
        val resp = Http.client(useProxy = false).newCall(req).awaitResponse()
        resp.use { r ->
            if (r.code >= 400) throw ApiException("HTTP ${r.code}: ${r.body?.string()?.take(300) ?: ""}")
            val setCookies = r.headers("Set-Cookie")
            val jar = linkedMapOf<String, String>()
            setCookies.forEach { c ->
                val first = c.substringBefore(';')
                val idx = first.indexOf('=')
                if (idx > 0) jar[first.substring(0, idx).trim()] = first.substring(idx + 1).trim()
            }
            if (jar.isNotEmpty()) cookies = jar.map { "${it.key}=${it.value}" }.joinToString("; ")
        }
    }

    suspend fun importCredential(cred: BuddyCredential): Pair<String, String> {
        val url = baseURL + "/admin/account/import"
        val payload = JSONObject().apply {
            put("access_token", cred.auth.accessToken)
            put("refresh_token", cred.auth.refreshToken)
            put("token_type", "Bearer")
            put("expires_at", cred.auth.expiresAt)
            put("domain", cred.auth.domain)
            put("nickname", cred.account.nickname)
        }.toString()
        val b = Request.Builder()
            .url(url)
            .post(payload.toRequestBody(jsonMedia))
            .addHeader("Content-Type", "application/json")
        if (cookies.isNotEmpty()) b.addHeader("Cookie", cookies)
        val resp = Http.client(useProxy = false).newCall(b.build()).awaitResponse()
        val text = resp.use { r ->
            val body = r.body?.string() ?: ""
            if (r.code >= 400) throw ApiException("HTTP ${r.code}: ${body.take(300)}")
            body
        }
        val obj = runCatching { JSONObject(text) }.getOrNull()
            ?: throw ApiException("响应解析失败: ${text.take(200)}")
        if (obj.optBoolean("ok", false)) {
            return obj.optString("uid", "") to obj.optString("nickname", "")
        }
        throw ApiException("登录失败或凭证被拒: ${text.take(300)}")
    }
}
