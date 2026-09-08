package cn.buddykeymanager.app.web

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** 登录页自动填充（手机号 / 验证码） */
object LoginAutofill {

    fun fillPhone(webView: WebView?, phone: String) {
        if (webView == null || phone.isEmpty()) return
        val js = """
        (function(){
            try {
                var methodTexts = document.querySelectorAll('.login-method-text');
                var phoneMethod = null;
                for (var i=0;i<methodTexts.length;i++){
                    if (methodTexts[i].textContent.indexOf('手机号') >= 0) { phoneMethod = methodTexts[i]; break; }
                }
                if (phoneMethod) {
                    var active = phoneMethod.closest('.login-method-item') || phoneMethod.parentElement;
                    var isActive = active && (active.className.indexOf('active') >= 0 || active.className.indexOf('selected') >= 0);
                    if (!isActive) { phoneMethod.click(); }
                }
            } catch(e){}

            var tries = 0;
            function waitAndFill() {
                var input = document.querySelector('input[placeholder="请输入你的手机号"]');
                if (!input) {
                    tries++;
                    if (tries < 20) { setTimeout(waitAndFill, 300); return; }
                    return;
                }
                var proto = Object.getPrototypeOf(input);
                var desc = Object.getOwnPropertyDescriptor(proto, 'value');
                if (desc && desc.set) { desc.set.call(input, '$phone'); } else { input.value = '$phone'; }
                input.dispatchEvent(new Event('input', {bubbles: true}));
                input.dispatchEvent(new Event('change', {bubbles: true}));
                setTimeout(function(){
                    try {
                        var cb = document.querySelector('input[type="checkbox"]');
                        if (cb && !cb.checked) { cb.click(); }
                    } catch(e){}
                }, 300);
                setTimeout(function(){
                    try {
                        var btn = document.querySelector('.oneid-react-verify-code__code-btn');
                        if (btn && btn.className.indexOf('disabled') < 0) { btn.click(); }
                    } catch(e){}
                }, 800);
            }
            setTimeout(waitAndFill, 500);
            return true;
        })();
        """.trimIndent()
        webView.post { webView.evaluateJavascript(js, null) }
    }

    fun fillCode(webView: WebView?, code: String) {
        if (webView == null || code.isEmpty()) return
        val js = """
        (function(){
            var input = document.querySelector('input[placeholder="请输入验证码"]');
            if (!input) return false;
            var proto = Object.getPrototypeOf(input);
            var desc = Object.getOwnPropertyDescriptor(proto, 'value');
            if (desc && desc.set) { desc.set.call(input, '$code'); } else { input.value = '$code'; }
            input.dispatchEvent(new Event('input', {bubbles: true}));
            input.dispatchEvent(new Event('change', {bubbles: true}));
            setTimeout(function(){
                try {
                    var btn = document.querySelector('.oneid-react-dialog-confirm-button');
                    if (btn && btn.className.indexOf('disabled') < 0) { btn.click(); }
                } catch(e){}
            }, 800);
            return true;
        })();
        """.trimIndent()
        webView.post { webView.evaluateJavascript(js, null) }
    }
}

/** 全量清除浏览器数据（登录成功后调用） */
object WebWipe {
    fun wipeAll(webView: WebView?, redirectTo: String? = null) {
        val wv = webView ?: return
        wv.post {
            wv.evaluateJavascript(
                """
                (function(){
                    try { var c = document.cookie.split(';'); for (var i=0;i<c.length;i++){ document.cookie = c[i].split('=')[0] + '=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/; domain=.' + location.hostname; document.cookie = c[i].split('=')[0] + '=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'; } } catch(e){}
                    try { localStorage.clear(); sessionStorage.clear(); } catch(e){}
                    try { indexedDB.databases().then(function(dbs){ dbs.forEach(function(db){ indexedDB.deleteDatabase(db.name); }); }); } catch(e){}
                    try { navigator.serviceWorker.getRegistrations().then(function(regs){ regs.forEach(function(r){ r.unregister(); }); }); } catch(e){}
                })();
                """.trimIndent(), null
            )
            runCatching {
                val cm = CookieManager.getInstance()
                cm.removeAllCookies(null)
                cm.flush()
                wv.clearCache(true)
                wv.clearFormData()
                wv.clearHistory()
                WebStorage.getInstance().deleteAllData()
            }
            redirectTo?.let { wv.loadUrl(it) }
        }
    }
}

private fun randomChromeUA(): String {
    val list = listOf(
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; SM-S911B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 12; Redmi K50) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 14; Xiaomi 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; V2217A) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    )
    return list.random()
}

/** 无痕浏览器：独立数据目录 + 禁用缓存/Cookie 落盘 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun IncognitoWebView(
    url: String,
    modifier: Modifier = Modifier,
    onTitleChange: (String) -> Unit = {},
    onUrlChange: (String) -> Unit = {},
    onCreated: (WebView) -> Unit = {}
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = false
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(true)
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    userAgentString = randomChromeUA()
                }
                runCatching { CookieManager.getInstance().setAcceptCookie(false) }
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, u: String?, favicon: Bitmap?) {
                        u?.let { onUrlChange(it) }
                    }

                    override fun onPageFinished(view: WebView?, u: String?) {
                        onTitleChange(view?.title ?: "")
                        u?.let { onUrlChange(it) }
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, u: String?): Boolean {
                        u?.let { onUrlChange(it) }
                        return false
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        onTitleChange(title ?: "")
                    }

                    override fun onCreateWindow(
                        view: WebView?,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: Message?
                    ): Boolean {
                        val v = view ?: return false
                        val msg = resultMsg ?: return false
                        val transport = msg.obj as? WebView.WebViewTransport ?: return false
                        transport.webView = v
                        msg.sendToTarget()
                        return true
                    }
                }
                loadUrl(url)
                // 延迟到主线程消息队列回调，避免在 AndroidView factory（组合/测量阶段）同步写入 Compose 状态导致崩溃
                post { onCreated(this) }
            }
        },
        update = { }
    )
}
