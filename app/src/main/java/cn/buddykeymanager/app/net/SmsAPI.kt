package cn.buddykeymanager.app.net

import okhttp3.MultipartBody
import okhttp3.Request
import org.json.JSONObject
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** ejiema 接码平台（GET 明文返回） */
object SmsAPI {

    private const val BASE = "https://api.ejiema.com/zc/data.php"

    private suspend fun call(params: Map<String, String>, useProxy: Boolean = true): String {
        val url = buildString {
            append(BASE).append('?')
            append(params.entries.joinToString("&") { "${it.key}=${java.net.URLEncoder.encode(it.value, "UTF-8")}" })
        }
        val req = Request.Builder().url(url).get().build()
        val resp = Http.client(useProxy).newCall(req).awaitResponse()
        val text = resp.use { r ->
            val body = r.body?.string() ?: ""
            if (r.code >= 400) throw ApiException("HTTP ${r.code}")
            body
        }.trim()
        if (text.startsWith("ERROR:")) throw ApiException(text)
        return text
    }

    suspend fun leftAmount(token: String) = call(mapOf("code" to "leftAmount", "token" to token))

    suspend fun getPhone(token: String, keyword: String, card: String = "全部"): String {
        val p = mutableMapOf("code" to "getPhone", "token" to token)
        if (keyword.isNotEmpty()) p["keyWord"] = keyword
        if (card.isNotEmpty()) p["cardType"] = card
        return call(p)
    }

    /** null = 尚未收到 */
    suspend fun getMsg(token: String, phone: String, keyword: String): String? {
        val p = mutableMapOf("code" to "getMsg", "token" to token, "phone" to phone)
        if (keyword.isNotEmpty()) p["keyWord"] = keyword
        val text = call(p)
        if (text.contains("尚未收到")) return null
        return text
    }

    suspend fun release(token: String, phone: String) =
        call(mapOf("code" to "release", "token" to token, "phone" to phone))

    suspend fun block(token: String, phone: String) =
        call(mapOf("code" to "block", "token" to token, "phone" to phone))

    /** 从短信内容提取验证码 */
    fun extractCode(msg: String): String? {
        val patterns = listOf(
            Regex("验证码\\s*(是|为|：|:)?\\s*([0-9]{4,8})"),
            Regex("code\\s*(是|为|：|:)?\\s*([0-9]{4,8})", RegexOption.IGNORE_CASE),
            Regex("[：:，,\\s]([0-9]{6})([，,。\\s]|$)"),
            Regex("([0-9]{6})([，,。]|$)")
        )
        for (p in patterns) {
            val m = p.find(msg) ?: continue
            val digits = m.value.filter { it.isDigit() }
            if (digits.length in 4..8) return digits
        }
        val parts = msg.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }
        for (part in parts.reversed()) {
            if (part.length in 4..8) return part
        }
        return null
    }
}

/** workbuddy.svipxx.cn 验证码接收平台（卡密制，AES-128-CBC） */
object SvipxxSmsAPI {

    private const val API = "https://workbuddy.svipxx.cn/api.php"
    private const val AES_KEY = "xXjM2026#cardK!y"
    private const val AES_IV = "a6s8d0f2g4h6j8k0"

    fun encryptCardKey(card: String): String? = try {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(AES_KEY.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(AES_IV.toByteArray(Charsets.UTF_8))
        )
        val enc = cipher.doFinal(card.toByteArray(Charsets.UTF_8))
        Base64.getEncoder().encodeToString(enc)
    } catch (e: Exception) {
        null
    }

    private suspend fun call(action: String, card: String): JSONObject {
        val encrypted = encryptCardKey(card) ?: throw ApiException("卡密加密失败")
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("action", action)
            .addFormDataPart("card_key", encrypted)
            .build()
        val req = Request.Builder().url(API).post(body).build()
        val resp = Http.client(useProxy = false).newCall(req).awaitResponse()
        val text = resp.use { r ->
            val b = r.body?.string() ?: ""
            if (r.code >= 400) throw ApiException("HTTP ${r.code}")
            b
        }
        return runCatching { JSONObject(text) }.getOrElse { throw ApiException("响应解析失败: ${text.take(200)}") }
    }

    suspend fun status(card: String) = call("status", card)
    suspend fun getPhone(card: String) = call("get_phone", card)
    suspend fun getSms(card: String) = call("get_sms", card)
    suspend fun changePhone(card: String) = call("change_phone", card)

    /** 从响应解析短信 / 验证码（兼容各种 data 结构） */
    fun extractSms(resp: JSONObject): String? {
        resp.optString("yzm").let { if (it.isNotEmpty() && it != "0") return "验证码：$it" }
        resp.optString("sms").let { if (it.isNotEmpty()) return it }
        val data = resp.optJSONObject("data")
        if (data != null) {
            data.optString("sms").let { if (it.isNotEmpty()) return it }
            data.optString("yzm").let { if (it.isNotEmpty() && it != "0") return "验证码：$it" }
            data.optString("verify_code").let { if (it.isNotEmpty() && it != "0") return "验证码：$it" }
            data.optString("sms_content").let { if (it.isNotEmpty()) return it }
        }
        resp.optJSONArray("data")?.let { arr ->
            if (arr.length() > 0) {
                val s = (0 until arr.length()).joinToString(" ") { arr.optString(it) }
                if (s.isNotBlank() && s != "[]") return s
            }
        }
        (resp.opt("data") as? String)?.let { if (it.isNotEmpty() && it != "[]") return it }
        return null
    }
}
