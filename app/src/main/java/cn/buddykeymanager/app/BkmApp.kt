package cn.buddykeymanager.app

import android.app.Application
import android.util.Log
import cn.buddykeymanager.app.store.CredentialStore
import cn.buddykeymanager.app.store.Prefs
import cn.buddykeymanager.app.util.Notify
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class BkmApp : Application() {

    companion object {
        private const val CRASH_FILE = "bkm_crash.log"
    }

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
        Prefs.init(this)
        CredentialStore.init(this)
        Notify.createChannel(this)
        reportPendingCrash()
    }

    /** 捕获未处理异常并落盘，便于下次启动在「日志」页查看崩溃堆栈 */
    private fun installCrashHandler() {
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                File(filesDir, CRASH_FILE).writeText(sw.toString())
                Log.e("BkmApp", "FATAL crash", throwable)
            }
            default?.uncaughtException(thread, throwable)
        }
    }

    /** 若上次存在崩溃日志，则并入操作日志供用户查看 */
    private fun reportPendingCrash() {
        runCatching {
            val f = File(filesDir, CRASH_FILE)
            if (f.exists()) {
                val text = f.readText()
                CredentialStore.appendLog("💥 上次崩溃日志：\n$text")
                f.delete()
            }
        }
    }
}
