package cn.buddykeymanager.app.net

import cn.buddykeymanager.app.store.Prefs
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class ReleaseInfo(
    val tagName: String,
    val name: String,
    val htmlUrl: String,
    val body: String,
    val apkUrl: String?
)

sealed class UpdateResult {
    data class Available(val release: ReleaseInfo) : UpdateResult()
    object UpToDate : UpdateResult()
    data class Failed(val message: String) : UpdateResult()
}

/** GitHub Releases 自动更新检查（指向用户私有仓库 turbomind66/BuddyKeyManager-Android） */
object Updater {

    private const val REPO = "turbomind66/BuddyKeyManager-Android"

    suspend fun check(currentVersion: String): UpdateResult {
        return try {
            val url = "https://api.github.com/repos/$REPO/releases/latest"
            val b = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "BuddyKeyManager-Android")
            val token = Prefs.githubToken.value.trim()
            if (token.isNotEmpty()) b.header("Authorization", "Bearer $token")

            val resp = Http.client().newCall(b.build()).awaitResponse()
            val code = resp.code
            val raw = resp.use { r -> r.body?.string() ?: "" }

            when (code) {
                404 -> UpdateResult.Failed("未找到 Release（私有仓库需在「设置」页配置 GitHub Token）")
                401, 403 -> UpdateResult.Failed("GitHub Token 无效或无权限（HTTP $code）")
                !in 200..299 -> UpdateResult.Failed("检查更新失败（HTTP $code）")
                else -> {
                    val obj = JSONObject(raw)
                    val tag = obj.optString("tag_name", "")
                    val release = ReleaseInfo(
                        tagName = tag,
                        name = obj.optString("name", tag),
                        htmlUrl = obj.optString("html_url", ""),
                        body = obj.optString("body", ""),
                        apkUrl = extractApk(obj.optJSONArray("assets"))
                    )
                    if (isNewer(tag, currentVersion)) UpdateResult.Available(release)
                    else UpdateResult.UpToDate
                }
            }
        } catch (e: Exception) {
            UpdateResult.Failed(e.message ?: "网络错误")
        }
    }

    private fun extractApk(assets: JSONArray?): String? {
        if (assets == null) return null
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            if (a.optString("name", "").endsWith(".apk")) {
                return a.optString("browser_download_url", "").ifEmpty { null }
            }
        }
        return null
    }

    private fun isNewer(remoteTag: String, current: String): Boolean {
        val r = parseVersion(remoteTag) ?: return false
        val c = parseVersion(current) ?: return false
        for (i in 0 until maxOf(r.size, c.size)) {
            val x = r.getOrElse(i) { 0 }
            val y = c.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parseVersion(v: String): List<Int>? {
        val s = v.trim().removePrefix("v").removePrefix("V")
        val parts = s.split(".").mapNotNull { it.toIntOrNull() }
        return if (parts.isEmpty()) null else parts
    }
}
