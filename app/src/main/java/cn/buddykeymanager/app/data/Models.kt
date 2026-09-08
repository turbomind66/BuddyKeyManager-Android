package cn.buddykeymanager.app.data

import android.util.Base64
import org.json.JSONObject
import java.util.UUID

// MARK: - 数据模型（与 iOS 版 / workbuddy2api 落盘格式兼容）

data class BuddyAccount(
    var uid: String = "",
    var enterpriseId: String = "",
    var nickname: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("uid", uid)
        put("enterpriseId", enterpriseId)
        put("nickname", nickname)
    }

    companion object {
        fun fromJson(o: JSONObject?): BuddyAccount = BuddyAccount(
            uid = o?.optString("uid") ?: "",
            enterpriseId = o?.optString("enterpriseId") ?: "",
            nickname = o?.optString("nickname") ?: ""
        )
    }
}

data class BuddyAuthData(
    var accessToken: String = "",
    var refreshToken: String = "",
    var expiresAt: Long = 0L,
    var domain: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("accessToken", accessToken)
        put("refreshToken", refreshToken)
        put("expiresAt", expiresAt)
        put("domain", domain)
    }

    companion object {
        fun fromJson(o: JSONObject?): BuddyAuthData = BuddyAuthData(
            accessToken = o?.optString("accessToken") ?: "",
            refreshToken = o?.optString("refreshToken") ?: "",
            expiresAt = o?.optLong("expiresAt") ?: 0L,
            domain = o?.optString("domain") ?: ""
        )
    }
}

data class BuddyCredential(
    var account: BuddyAccount = BuddyAccount(),
    var auth: BuddyAuthData = BuddyAuthData(),
    var note: String? = null,
    var savedAt: Long = 0L,
    var balance: Long = -1L,
    var alive: Boolean? = null,
    var riskControlled: Boolean? = null
) {
    val id: String get() = account.uid

    val displayName: String
        get() = when {
            !note.isNullOrEmpty() -> note!!
            account.nickname.isNotEmpty() -> account.nickname
            else -> account.uid
        }

    val subInfo: String
        get() {
            val nick = account.nickname
            val n = note
            return if (!n.isNullOrEmpty() && nick.isNotEmpty() && nick != n) "（$nick）" else ""
        }

    val initial: String
        get() {
            val n = displayName
            val s = if (n.isEmpty()) account.uid else n
            return (if (s.isEmpty()) "?" else s.substring(0, 1)).uppercase()
        }

    fun copyDeep(): BuddyCredential = copy(account = account.copy(), auth = auth.copy())

    fun toJson(): JSONObject = JSONObject().apply {
        put("account", account.toJson())
        put("auth", auth.toJson())
        note?.let { put("note", it) }
        put("savedAt", savedAt)
        put("balance", balance)
        alive?.let { put("alive", it) }
        riskControlled?.let { put("riskControlled", it) }
    }

    companion object {
        fun fromJson(o: JSONObject): BuddyCredential = BuddyCredential(
            account = BuddyAccount.fromJson(o.optJSONObject("account")),
            auth = BuddyAuthData.fromJson(o.optJSONObject("auth")),
            note = o.optString("note", "").let { if (it.isEmpty()) null else it },
            savedAt = o.optLong("savedAt", 0L),
            balance = o.optLong("balance", -1L),
            alive = if (o.has("alive")) o.optBoolean("alive") else null,
            riskControlled = if (o.has("riskControlled")) o.optBoolean("riskControlled") else null
        )

        fun from(
            token: TokenData,
            uid: String,
            nickname: String,
            domain: String,
            now: Long = System.currentTimeMillis() / 1000
        ): BuddyCredential = BuddyCredential(
            account = BuddyAccount(uid = uid, enterpriseId = "", nickname = nickname),
            auth = BuddyAuthData(
                accessToken = token.accessToken,
                refreshToken = token.refreshToken,
                expiresAt = if (token.expiresIn > 0) now + token.expiresIn else now + 5184000L,
                domain = domain.ifEmpty { "www.codebuddy.cn" }
            ),
            note = null,
            savedAt = now,
            balance = -1L
        )
    }
}

data class TokenData(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val domain: String?
)

enum class SessionStatus { PENDING, POLLING, COMPLETED, FAILED }

data class AuthSession(
    var id: String = UUID.randomUUID().toString(),
    var state: String = "",
    var authUrl: String = "",
    var platform: String = "CLI",
    var createdAt: Long = System.currentTimeMillis(),
    var status: SessionStatus = SessionStatus.PENDING,
    var accountUid: String? = null,
    var lastError: String? = null,
    var tokenExpiresAt: Long? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("state", state)
        put("authUrl", authUrl)
        put("platform", platform)
        put("createdAt", createdAt)
        put("status", status.name)
        accountUid?.let { put("accountUid", it) }
        lastError?.let { put("lastError", it) }
        tokenExpiresAt?.let { put("tokenExpiresAt", it) }
    }

    companion object {
        fun fromJson(o: JSONObject): AuthSession = AuthSession(
            id = o.optString("id", UUID.randomUUID().toString()),
            state = o.optString("state"),
            authUrl = o.optString("authUrl"),
            platform = o.optString("platform", "CLI"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            status = runCatching { SessionStatus.valueOf(o.optString("status", "PENDING")) }.getOrDefault(SessionStatus.PENDING),
            accountUid = o.optString("accountUid", "").let { if (it.isEmpty()) null else it },
            lastError = o.optString("lastError", "").let { if (it.isEmpty()) null else it },
            tokenExpiresAt = if (o.has("tokenExpiresAt")) o.optLong("tokenExpiresAt") else null
        )
    }
}

// MARK: - JWT 解码（uid / nickname 来自 access_token payload，不校验签名）

object JWTDecoder {
    fun decodePayload(token: String): JSONObject? {
        val parts = token.split(".")
        if (parts.size < 2) return null
        return try {
            val payload = parts[1]
            val pad = "=".repeat((4 - payload.length % 4) % 4)
            val data = Base64.decode(
                payload.replace('-', '+').replace('_', '/') + pad,
                Base64.DEFAULT
            )
            JSONObject(String(data, Charsets.UTF_8))
        } catch (e: Exception) {
            null
        }
    }

    /** 返回 (uid, nickname) */
    fun userInfo(token: String): Pair<String, String> {
        val obj = decodePayload(token) ?: return "" to ""
        val uid = obj.optString("sub", "")
        val nick = obj.optString("preferred_username").ifEmpty { obj.optString("email") }
        return uid to nick
    }
}
