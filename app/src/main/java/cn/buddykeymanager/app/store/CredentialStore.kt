package cn.buddykeymanager.app.store

import android.content.Context
import cn.buddykeymanager.app.data.AuthSession
import cn.buddykeymanager.app.data.BuddyCredential
import cn.buddykeymanager.app.data.JWTDecoder
import cn.buddykeymanager.app.data.SessionStatus
import cn.buddykeymanager.app.net.ApiException
import cn.buddykeymanager.app.net.BuddyAPI
import cn.buddykeymanager.app.net.PushAPI
import cn.buddykeymanager.app.net.ResourcePackage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** 凭证存储 + 轮询入库 + 测活 + 推送（iOS CredentialStore 的 Android 对应实现） */
object CredentialStore {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val credentials = MutableStateFlow<List<BuddyCredential>>(emptyList())
    val sessions = MutableStateFlow<List<AuthSession>>(emptyList())
    val log = MutableStateFlow("")
    val creating = MutableStateFlow(false)

    private val pollJobs = ConcurrentHashMap<String, Job>()
    private val attemptCounts = ConcurrentHashMap<String, Int>()

    private lateinit var credFile: File
    private lateinit var sessFile: File

    fun init(ctx: Context) {
        credFile = File(ctx.filesDir, "credentials.json")
        sessFile = File(ctx.filesDir, "sessions.json")
        load()
    }

    private fun api() = BuddyAPI(Prefs.region.value)
    private fun regionLabel() = if (Prefs.region.value == "global") "国际区" else "中国区"

    // MARK: - 持久化

    private fun load() {
        runCatching {
            if (credFile.exists()) {
                val arr = JSONArray(credFile.readText())
                val list = mutableListOf<BuddyCredential>()
                for (i in 0 until arr.length()) list.add(BuddyCredential.fromJson(arr.getJSONObject(i)))
                credentials.value = list
            }
        }
        runCatching {
            if (sessFile.exists()) {
                val arr = JSONArray(sessFile.readText())
                val list = mutableListOf<AuthSession>()
                for (i in 0 until arr.length()) list.add(AuthSession.fromJson(arr.getJSONObject(i)))
                sessions.value = list
            }
        }
        appendLog("加载完成：${credentials.value.size} 个凭证，${sessions.value.size} 个会话")
    }

    private fun persist() {
        runCatching {
            val ca = JSONArray()
            credentials.value.forEach { ca.put(it.toJson()) }
            credFile.writeText(ca.toString(2))
            val sa = JSONArray()
            sessions.value.forEach { sa.put(it.toJson()) }
            sessFile.writeText(sa.toString(2))
        }
    }

    fun appendLog(msg: String) {
        val line = "[${SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date())}] $msg"
        log.value = (line + "\n" + log.value).let { if (it.length > 8000) it.substring(0, 8000) else it }
    }

    // MARK: - 创建授权会话

    fun createAuthSession(platform: String = "CLI") {
        scope.launch {
            creating.value = true
            try {
                val (authUrl, state) = api().createAuthSession(platform)
                val sess = AuthSession(
                    state = state,
                    authUrl = authUrl,
                    platform = platform,
                    status = SessionStatus.PENDING
                )
                sessions.value = listOf(sess) + sessions.value
                persist()
                appendLog("已创建授权会话 ${sess.id.take(8)}（${regionLabel()}）")
                startPolling(sess.id)
            } catch (e: Exception) {
                appendLog("创建授权链接失败: ${e.message}")
            } finally {
                creating.value = false
            }
        }
    }

    // MARK: - 轮询（每 3 秒，最长 10 分钟）

    fun startPolling(sessionId: String) {
        stopPolling(sessionId)
        pollJobs[sessionId] = scope.launch {
            var attempts = 0
            while (attempts < 100) {
                delay(3_000)
                attempts++
                if (pollOnce(sessionId)) return@launch
            }
            failSession(sessionId, "等待登录超时（10分钟）")
            stopPolling(sessionId)
        }
    }

    fun stopPolling(sessionId: String) {
        pollJobs[sessionId]?.cancel()
        pollJobs.remove(sessionId)
    }

    private fun failSession(sessionId: String, reason: String) {
        sessions.value = sessions.value.map {
            if (it.id == sessionId) it.copy(status = SessionStatus.FAILED, lastError = reason) else it
        }
        persist()
        appendLog("会话 ${sessionId.take(8)} 失败: $reason")
    }

    /** 返回 true = 已完成（入库成功） */
    private suspend fun pollOnce(sessionId: String): Boolean {
        val idx = sessions.value.indexOfFirst { it.id == sessionId }
        if (idx < 0) { stopPolling(sessionId); return true }
        val sess = sessions.value[idx]
        if (sess.status != SessionStatus.PENDING && sess.status != SessionStatus.POLLING) {
            stopPolling(sessionId)
            return true
        }
        if (sess.status != SessionStatus.POLLING) {
            sessions.value = sessions.value.map { if (it.id == sessionId) it.copy(status = SessionStatus.POLLING) else it }
            persist()
            appendLog("开始轮询 token（state=${sess.state.take(8)}…）")
        }
        return try {
            val tok = api().pollToken(sess.state) ?: return false
            val (uid, nickname) = JWTDecoder.userInfo(tok.accessToken)
            if (uid.isEmpty()) {
                appendLog("JWT 解析 uid 失败，继续轮询")
                return false
            }
            val domain = tok.domain ?: api().region.domain
            var cred = BuddyCredential.from(tok, uid, nickname.ifEmpty { uid.take(8) }, domain)
            val existingIdx = credentials.value.indexOfFirst { it.account.uid == uid }
            if (existingIdx >= 0) {
                val old = credentials.value[existingIdx]
                cred = cred.copy(note = old.note, balance = old.balance, savedAt = old.savedAt, alive = old.alive)
                credentials.value = credentials.value.toMutableList().apply { this[existingIdx] = cred }
                appendLog("凭证已更新: $nickname（${regionLabel()}）")
            } else {
                credentials.value = listOf(cred) + credentials.value
                appendLog("新凭证入库: $nickname（${regionLabel()}）")
            }
            sessions.value = sessions.value.map {
                if (it.id == sessionId) it.copy(
                    status = SessionStatus.COMPLETED,
                    accountUid = uid,
                    tokenExpiresAt = cred.auth.expiresAt,
                    lastError = null
                ) else it
            }
            persist()
            stopPolling(sessionId)
            appendLog("✅ 登录成功，凭证已保存（过期 ${fmtExpiry(cred.auth.expiresAt)}）")
            true
        } catch (e: Exception) {
            val n = (attemptCounts[sessionId] ?: 0) + 1
            attemptCounts[sessionId] = n
            if (n % 10 == 1) appendLog("轮询 ${sess.state.take(8)} 出错: ${e.message}")
            false
        }
    }

    private fun fmtExpiry(ts: Long): String =
        SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(ts * 1000))

    // MARK: - 刷新

    fun refreshCredential(cred: BuddyCredential) {
        scope.launch {
            try {
                val tok = api().refresh(cred)
                val idx = credentials.value.indexOfFirst { it.account.uid == cred.account.uid }
                if (idx >= 0) {
                    val old = credentials.value[idx]
                    val updated = old.copyDeep().apply {
                        auth.accessToken = tok.accessToken
                        if (tok.refreshToken.isNotEmpty()) auth.refreshToken = tok.refreshToken
                        if (!tok.domain.isNullOrEmpty()) auth.domain = tok.domain!!
                        if (tok.expiresIn > 0) auth.expiresAt = System.currentTimeMillis() / 1000 + tok.expiresIn
                    }
                    credentials.value = credentials.value.toMutableList().apply { this[idx] = updated }
                    sessions.value = sessions.value.map {
                        if (it.accountUid == cred.account.uid) it.copy(tokenExpiresAt = updated.auth.expiresAt) else it
                    }
                    persist()
                    appendLog("已刷新凭证 ${cred.account.nickname}: 新 token 有效期 ${fmtExpiry(updated.auth.expiresAt)}")
                }
            } catch (e: Exception) {
                appendLog("刷新凭证 ${cred.account.nickname} 失败: ${e.message}")
            }
        }
    }

    fun refreshAllIfNeeded(withinSeconds: Long = 3600 * 24) {
        val now = System.currentTimeMillis() / 1000
        credentials.value.filter { it.auth.expiresAt <= now + withinSeconds }.forEach { refreshCredential(it) }
    }

    // MARK: - 测活

    fun pingCredential(cred: BuddyCredential, onDone: ((Boolean, Boolean) -> Unit)? = null) {
        scope.launch {
            var alive = false
            var risk = false
            try {
                api().ping(cred)
                alive = true
                appendLog("✅ 测活成功: ${cred.account.nickname}（token 有效）")
            } catch (e: Exception) {
                val msg = e.message ?: ""
                when {
                    msg.contains("401") -> {
                        appendLog("测活 ${cred.account.nickname} 返回 401，尝试刷新…")
                        refreshNow(cred)
                        val updated = credentials.value.firstOrNull { it.account.uid == cred.account.uid } ?: cred
                        alive = try {
                            api().ping(updated)
                            appendLog("✅ 刷新后测活成功: ${cred.account.nickname}")
                            true
                        } catch (e2: Exception) {
                            appendLog("❌ 刷新后仍失败: ${e2.message}")
                            false
                        }
                    }
                    msg.contains("403") -> {
                        risk = true
                        appendLog("⚠️ 测活 403（风控拦截）: ${cred.account.nickname}")
                    }
                    else -> appendLog("❌ 测活失败: ${cred.account.nickname} — $msg")
                }
            }
            val idx = credentials.value.indexOfFirst { it.account.uid == cred.account.uid }
            if (idx >= 0) {
                credentials.value = credentials.value.toMutableList().apply {
                    this[idx] = this[idx].copyDeep().apply { this.alive = alive; this.riskControlled = risk }
                }
                persist()
            }
            onDone?.invoke(alive, risk)
        }
    }

    private suspend fun refreshNow(cred: BuddyCredential) {
        try {
            val tok = api().refresh(cred)
            val idx = credentials.value.indexOfFirst { it.account.uid == cred.account.uid }
            if (idx >= 0) {
                val updated = credentials.value[idx].copyDeep().apply {
                    auth.accessToken = tok.accessToken
                    if (tok.refreshToken.isNotEmpty()) auth.refreshToken = tok.refreshToken
                    if (tok.expiresIn > 0) auth.expiresAt = System.currentTimeMillis() / 1000 + tok.expiresIn
                }
                credentials.value = credentials.value.toMutableList().apply { this[idx] = updated }
            }
        } catch (e: Exception) {
            appendLog("刷新失败: ${e.message}")
        }
    }

    fun pingAll(onProgress: ((done: Int, total: Int) -> Unit)? = null, onDone: ((ok: Int, risk: Int, dead: Int) -> Unit)? = null) {
        scope.launch {
            val list = credentials.value.toList()
            if (list.isEmpty()) { onDone?.invoke(0, 0, 0); return@launch }
            appendLog("🔍 开始批量测活 ${list.size} 个凭证…")
            var ok = 0; var risk = 0; var dead = 0
            list.forEachIndexed { i, cred ->
                kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
                    pingCredential(cred) { alive, isRisk ->
                        if (alive) ok++ else if (isRisk) risk++ else dead++
                        onProgress?.invoke(i + 1, list.size)
                        if (cont.isActive) cont.resumeWith(Result.success(Unit))
                    }
                }
            }
            appendLog("🔍 批量测活完成: 有效 $ok，风控 $risk，失效 $dead")
            onDone?.invoke(ok, risk, dead)
        }
    }

    // MARK: - 余额

    suspend fun queryResource(cred: BuddyCredential): Pair<Long, List<ResourcePackage>> = try {
        val pkgs = api().userResource(cred)
        var total = 0L
        pkgs.forEach { p ->
            val r = when {
                p.cycleSize > 0 -> p.cycleRemain
                p.cycleRemain > 0 || p.cycleUsed > 0 -> p.cycleRemain
                else -> p.capacityRemain
            }
            total += kotlin.math.max(r, 0L)
        }
        val idx = credentials.value.indexOfFirst { it.account.uid == cred.account.uid }
        if (idx >= 0) {
            credentials.value = credentials.value.toMutableList().apply {
                this[idx] = this[idx].copyDeep().apply { balance = total }
            }
            persist()
        }
        appendLog("💰 余额查询: ${cred.account.nickname} 剩余 $total 积分（${pkgs.size} 个套餐）")
        total to pkgs
    } catch (e: Exception) {
        appendLog("❌ 余额查询失败: ${cred.account.nickname} — ${e.message}")
        0L to emptyList()
    }

    // MARK: - 标注 / 删除

    fun setNote(uid: String, note: String) {
        val trimmed = note.trim()
        val idx = credentials.value.indexOfFirst { it.account.uid == uid }
        if (idx >= 0) {
            credentials.value = credentials.value.toMutableList().apply {
                this[idx] = this[idx].copyDeep().apply { this.note = trimmed.ifEmpty { null } }
            }
            persist()
            appendLog("标注 ${credentials.value[idx].displayName}: ${trimmed.ifEmpty { "已清除" }}")
        }
    }

    fun deleteCredential(cred: BuddyCredential) {
        credentials.value = credentials.value.filter { it.account.uid != cred.account.uid }
        persist()
        appendLog("已删除凭证 ${cred.account.nickname}")
    }

    fun deleteSession(sess: AuthSession) {
        stopPolling(sess.id)
        sessions.value = sessions.value.filter { it.id != sess.id }
        persist()
    }

    // MARK: - 推送

    suspend fun pushCredential(cred: BuddyCredential): Pair<String, String> {
        val base = Prefs.pushBaseURL.value.trim()
        if (base.isEmpty()) {
            val err = "未配置服务器地址，请到设置页填写"
            appendLog("❌ 推送失败: ${cred.account.nickname} — $err")
            throw ApiException(err)
        }
        return try {
            val api = PushAPI(base)
            api.login(Prefs.pushPassword.value)
            val res = api.importCredential(cred)
            appendLog("📤 推送成功: ${cred.account.nickname} → ${res.second.ifEmpty { res.first }}")
            res
        } catch (e: Exception) {
            appendLog("❌ 推送失败: ${cred.account.nickname} — ${e.message}")
            throw e
        }
    }

    suspend fun pushAll(): Pair<Int, List<String>> {
        val list = credentials.value.toList()
        if (list.isEmpty()) return 0 to emptyList()
        appendLog("📤 开始批量推送 ${list.size} 个凭证…")
        var ok = 0
        val failed = mutableListOf<String>()
        list.forEach { cred ->
            runCatching { pushCredential(cred) }
                .onSuccess { ok++ }
                .onFailure { failed.add("${cred.account.nickname}: ${it.message}") }
        }
        appendLog("📤 批量推送完成: 成功 $ok 个，失败 ${failed.size} 个")
        return ok to failed
    }
}
