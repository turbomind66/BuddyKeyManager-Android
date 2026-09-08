package cn.buddykeymanager.app.net

import cn.buddykeymanager.app.store.Prefs
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/** OkHttp 封装：按代理配置构建客户端（SOCKS5，可带用户名密码） */
object Http {

    private var cachedProxyClient: OkHttpClient? = null
    private var cachedProxyFingerprint: String? = null
    private var cachedDirectClient: OkHttpClient? = null

    @Synchronized
    fun client(useProxy: Boolean = true): OkHttpClient {
        if (!useProxy) {
            if (cachedDirectClient == null) cachedDirectClient = build(false)
            return cachedDirectClient!!
        }
        val fp = Prefs.proxy.value.fingerprint
        if (cachedProxyClient == null || cachedProxyFingerprint != fp) {
            cachedProxyClient = build(true)
            cachedProxyFingerprint = fp
        }
        return cachedProxyClient!!
    }

    private fun build(useProxy: Boolean): OkHttpClient {
        val b = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
        val p = Prefs.proxy.value
        if (useProxy && p.valid) {
            b.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress(p.host.trim(), p.port)))
            if (p.username.isNotBlank()) {
                val user = p.username
                val pass = p.password
                b.proxyAuthenticator { _, response ->
                    response.request.newBuilder()
                        .header("Proxy-Authorization", Credentials.basic(user, pass))
                        .build()
                }
            }
        }
        return b.build()
    }
}

suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            cont.resumeWith(Result.success(response))
        }

        override fun onFailure(call: Call, e: IOException) {
            if (cont.isCancelled) return
            cont.resumeWith(Result.failure(e))
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}

fun Request.Builder.withHeaders(headers: Map<String, String>): Request.Builder = apply {
    headers.forEach { (k, v) -> addHeader(k, v) }
}
