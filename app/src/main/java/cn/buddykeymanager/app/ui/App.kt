package cn.buddykeymanager.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.buddykeymanager.app.BuildConfig
import cn.buddykeymanager.app.net.ReleaseInfo
import cn.buddykeymanager.app.net.UpdateResult
import cn.buddykeymanager.app.net.Updater
import cn.buddykeymanager.app.store.CredentialStore
import cn.buddykeymanager.app.theme.AppColors
import cn.buddykeymanager.app.theme.CardBackground
import cn.buddykeymanager.app.theme.PageBackground
import kotlinx.coroutines.delay

private data class TabItem(val title: String, val icon: String)

private val TABS = listOf(
    TabItem("授权", "🔗"),
    TabItem("凭证", "🔑"),
    TabItem("日志", "📋"),
    TabItem("设置", "⚙️"),
)

@Composable
fun AppRoot() {
    val sessions by CredentialStore.sessions.collectAsState()
    val credentials by CredentialStore.credentials.collectAsState()

    var tab by remember { mutableStateOf(0) }
    var detailSessionId by remember { mutableStateOf<String?>(null) }
    var detailCredUid by remember { mutableStateOf<String?>(null) }
    var updateInfo by remember { mutableStateOf<ReleaseInfo?>(null) }

    // 启动后静默检查更新
    LaunchedEffect(Unit) {
        delay(1500)
        val r = Updater.check(BuildConfig.VERSION_NAME)
        if (r is UpdateResult.Available) updateInfo = r.release
    }

    // 详情页数据源（若已被删除则自动回退）
    val openSession = detailSessionId?.let { id -> sessions.firstOrNull { it.id == id } }
    val openCred = detailCredUid?.let { uid -> credentials.firstOrNull { it.account.uid == uid } }

    BackHandler(enabled = openSession != null || openCred != null) {
        detailSessionId = null
        detailCredUid = null
    }

    Box(Modifier.fillMaxSize()) {
        when {
            openSession != null -> {
                SessionDetailScreen(session = openSession) {
                    detailSessionId = null
                }
            }
            openCred != null -> {
                CredentialDetailScreen(cred = openCred) {
                    detailCredUid = null
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PageBackground)
                        .statusBarsPadding()
                ) {
                    Box(Modifier.weight(1f)) {
                        when (tab) {
                            0 -> SessionsScreen(onOpenSession = { detailSessionId = it })
                            1 -> CredentialsScreen(onOpenCred = { detailCredUid = it })
                            2 -> LogScreen()
                            else -> SettingsScreen()
                        }
                    }
                    BottomBar(selected = tab, onSelect = { tab = it })
                }
            }
        }

        updateInfo?.let { info ->
            UpdateDialog(
                version = info.tagName,
                notes = info.body,
                url = if (info.htmlUrl.isNotEmpty()) info.htmlUrl else "https://github.com/turbomind66/BuddyKeyManager-Android/releases/latest",
                onDismiss = { updateInfo = null }
            )
        }
    }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackground)
            .navigationBarsPadding()
            .padding(vertical = 6.dp)
    ) {
        TABS.forEachIndexed { i, t ->
            val active = i == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(i) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(t.icon, fontSize = 20.sp)
                Text(
                    t.title,
                    fontSize = 11.sp,
                    color = if (active) AppColors.brand else Color.Gray,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
