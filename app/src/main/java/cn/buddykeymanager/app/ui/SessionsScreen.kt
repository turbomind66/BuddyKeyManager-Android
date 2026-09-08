package cn.buddykeymanager.app.ui

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.buddykeymanager.app.data.AuthSession
import cn.buddykeymanager.app.data.SessionStatus
import cn.buddykeymanager.app.store.CredentialStore
import cn.buddykeymanager.app.store.Prefs
import cn.buddykeymanager.app.store.SmsStore
import cn.buddykeymanager.app.theme.AppColors
import cn.buddykeymanager.app.theme.CardBackground
import cn.buddykeymanager.app.theme.SecondaryBackground
import cn.buddykeymanager.app.web.IncognitoWebView
import cn.buddykeymanager.app.web.WebWipe

// MARK: - 授权会话列表页

@Composable
fun SessionsScreen(onOpenSession: (String) -> Unit) {
    val sessions by CredentialStore.sessions.collectAsState()
    val creating by CredentialStore.creating.collectAsState()
    val region by Prefs.region.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        ScreenTitle("授权")
        Spacer(Modifier.height(8.dp))

        // 品牌卡
        HeaderCard()
        Spacer(Modifier.height(16.dp))

        // 创建授权卡
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("区域", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                SegmentedSelector(
                    options = listOf("中国区" to "cn", "国际区" to "global"),
                    selected = region,
                    onSelect = { Prefs.setRegion(it) }
                )
            }
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (creating) AppColors.disabledBrush else AppColors.brandGradient)
                    .clickable(enabled = !creating) { CredentialStore.createAuthSession() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (creating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        if (creating) "正在创建…" else "＋ 创建授权链接",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // 会话列表
        Text("授权会话", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(10.dp))

        if (sessions.isEmpty()) {
            GlassCard {
                EmptyState("🔗", "还没有授权会话", "点击上方「创建授权链接」开始\n登录后会在这里看到会话状态")
            }
        } else {
            sessions.forEach { sess ->
                SessionRow(sess, onOpen = { onOpenSession(sess.id) }, onDelete = { CredentialStore.deleteSession(sess) })
                Spacer(Modifier.height(10.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HeaderCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.brandGradient)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text("🔗", fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("WorkBuddy 授权管理", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("OAuth 设备流 · 无痕登录 · 自动入库", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "创建授权链接 → 内置无痕浏览器登录 → 凭证自动入库并持续轮询监听",
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun SessionRow(sess: AuthSession, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppColors.cornerRadius.dp))
            .background(CardBackground)
            .clickable { onOpen() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (c, icon) = when (sess.status) {
            SessionStatus.PENDING -> AppColors.warning to "⏳"
            SessionStatus.POLLING -> AppColors.brand to "🔄"
            SessionStatus.COMPLETED -> AppColors.success to "✅"
            SessionStatus.FAILED -> AppColors.danger to "❌"
        }
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sess.platform, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                if (sess.accountUid != null) {
                    Spacer(Modifier.width(6.dp))
                    PillBadge(AppColors.success, "已登录")
                }
                Spacer(Modifier.weight(1f))
                StatusBadge(sess.status)
            }
            Spacer(Modifier.height(3.dp))
            Text("state: ${sess.state.take(10)}…", fontSize = 11.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(fmtDateTimeMillis(sess.createdAt), fontSize = 11.sp, color = Color.Gray)
        }
        Spacer(Modifier.width(4.dp))
        Text(
            "🗑",
            fontSize = 14.sp,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onDelete() }
                .padding(6.dp)
        )
    }
}

// MARK: - 会话详情（内置浏览器 + 接码）

@Composable
fun SessionDetailScreen(session: AuthSession, onBack: () -> Unit) {
    val sessions by CredentialStore.sessions.collectAsState()
    val current = sessions.firstOrNull { it.id == session.id } ?: session

    var webTitle by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf(session.authUrl) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var didAutoWipe by remember { mutableStateOf(false) }

    // 进入详情页启动轮询
    LaunchedEffect(session.id) {
        if (current.status == SessionStatus.PENDING || current.status == SessionStatus.POLLING) {
            CredentialStore.startPolling(session.id)
        }
    }

    // 登录成功后自动全量清除
    LaunchedEffect(current.status) {
        if (current.status == SessionStatus.COMPLETED && !didAutoWipe) {
            didAutoWipe = true
            WebWipe.wipeAll(webViewRef, "https://www.baidu.com")
            CredentialStore.appendLog("✅ 登录成功，浏览器数据已自动全量清除，已跳转百度")
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 顶部栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("‹ 返回", color = AppColors.brand, fontSize = 16.sp, modifier = Modifier.clickable { onBack() }.padding(4.dp))
            Spacer(Modifier.width(8.dp))
            StatusBadge(current.status)
            Spacer(Modifier.weight(1f))
            if (current.status == SessionStatus.COMPLETED) {
                PillBadge(AppColors.success, "✅ 凭证已入库")
            }
        }

        // 浏览器区域
        Box(Modifier.weight(1f)) {
            IncognitoWebView(
                url = session.authUrl,
                onTitleChange = { webTitle = it },
                onUrlChange = { currentUrl = it },
                onCreated = { wv ->
                    webViewRef = wv
                    SmsStore.loginWebView = wv
                },
                modifier = Modifier.fillMaxSize()
            )
            // 右上角操作
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                ActionPill("刷新") { webViewRef?.reload() }
                Spacer(Modifier.height(6.dp))
                ActionPill("清除数据") { WebWipe.wipeAll(webViewRef, "https://www.baidu.com") }
            }
        }

        // 接码快捷栏
        SmsBar()

        // 底部信息
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SecondaryBackground)
                .padding(8.dp)
        ) {
            Text("页面标题：${webTitle.ifEmpty { "-" }}", fontSize = 11.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("当前地址：${currentUrl.ifEmpty { session.authUrl }}", fontSize = 10.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ActionPill(text: String, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

// MARK: - 接码快捷栏

@Composable
fun SmsBar() {
    val phone by SmsStore.phone.collectAsState()
    val code by SmsStore.code.collectAsState()
    val statusText by SmsStore.statusText.collectAsState()
    val isPolling by SmsStore.isPolling.collectAsState()
    val provider by Prefs.smsProvider.collectAsState()
    val autoMode by Prefs.smsAutoMode.collectAsState()
    val smsUrl by Prefs.smsUrl.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // workbuddy 模式：接码网址输入
        if (provider == "workbuddy") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = smsUrl,
                    onValueChange = { Prefs.setSmsUrl(it) },
                    placeholder = { Text("输入接码网址（含卡密）", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SecondaryBackground,
                        unfocusedContainerColor = SecondaryBackground,
                        focusedIndicatorColor = AppColors.brand,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                )
                Spacer(Modifier.width(8.dp))
                Text("▶", color = AppColors.brand, fontSize = 18.sp, modifier = Modifier.clickable { SmsStore.parseUrlAndRun() }.padding(4.dp))
            }
            Spacer(Modifier.height(6.dp))
        }

        // 全自动模式开关
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background((if (autoMode) AppColors.warning else Color.Gray).copy(alpha = 0.12f))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (autoMode) "⚡" else "⭕", fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                "全自动换号",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (autoMode) AppColors.warning else Color.Gray
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (autoMode) "15s 无短信自动刷新换号" else "180s 无短信自动换号",
                fontSize = 9.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = autoMode,
                onCheckedChange = { Prefs.setSmsAutoMode(it) },
                modifier = Modifier.size(32.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AppColors.warning
                )
            )
        }
        Spacer(Modifier.height(8.dp))

        // 手机号 + 操作
        Row(verticalAlignment = Alignment.CenterVertically) {
            val statusColor = when {
                isPolling -> AppColors.warning
                code.isNotEmpty() -> AppColors.success
                phone.isNotEmpty() -> AppColors.brand
                else -> Color.Gray
            }
            val statusIcon = when {
                isPolling -> "🔄"
                code.isNotEmpty() -> "✅"
                phone.isNotEmpty() -> "📱"
                else -> "📵"
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(statusColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(statusIcon, fontSize = 14.sp)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    phone.ifEmpty { "未取号" },
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (phone.isEmpty()) Color.Gray else AppColors.brand
                )
                Text(statusText, fontSize = 9.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))

            if (phone.isEmpty()) {
                Text(
                    "取号",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(AppColors.brandGradient)
                        .clickable { SmsStore.getPhone() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            } else {
                RoundAction("⏹", AppColors.warning) {
                    SmsStore.stopPolling()
                    SmsStore.statusText.value = "已停止（号码保留）"
                }
                Spacer(Modifier.width(6.dp))
                RoundAction("✖", AppColors.danger) { SmsStore.releasePhone() }
                Spacer(Modifier.width(6.dp))
                RoundAction("🔄", AppColors.brand) { SmsStore.rephone() }
            }
        }

        // 验证码显示
        if (code.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppColors.success.copy(alpha = 0.06f))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("验证码", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.width(8.dp))
                Text(code, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.success)
                Spacer(Modifier.weight(1f))
                Text(
                    "复制",
                    color = AppColors.success,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(AppColors.success.copy(alpha = 0.12f))
                        .clickable { copyToClipboard(code) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        } else if (isPolling) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SecondaryBackground)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = AppColors.warning)
                Spacer(Modifier.width(8.dp))
                Text("自动查询验证码中…", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun RoundAction(icon: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(icon, fontSize = 12.sp, color = color)
    }
}
