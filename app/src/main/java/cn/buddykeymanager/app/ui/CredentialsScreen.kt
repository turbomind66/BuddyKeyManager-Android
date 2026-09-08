package cn.buddykeymanager.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.buddykeymanager.app.data.BuddyCredential
import cn.buddykeymanager.app.net.ResourcePackage
import cn.buddykeymanager.app.store.CredentialStore
import cn.buddykeymanager.app.store.Prefs
import cn.buddykeymanager.app.theme.AppColors
import cn.buddykeymanager.app.theme.CardBackground
import cn.buddykeymanager.app.theme.SecondaryBackground
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class CredFilter(val label: String) {
    ALL("全部"), OK("有效"), RISK("风控"), DEAD("失效"), UNTESTED("未测");

    fun matches(c: BuddyCredential): Boolean = when (this) {
        ALL -> true
        OK -> c.alive == true && c.riskControlled != true
        RISK -> c.riskControlled == true
        DEAD -> c.alive == false && c.riskControlled != true
        UNTESTED -> c.alive == null
    }
}

// MARK: - 凭证列表页

@Composable
fun CredentialsScreen(onOpenCred: (String) -> Unit) {
    val credentials by CredentialStore.credentials.collectAsState()
    val scope = rememberCoroutineScope()

    var refreshing by remember { mutableStateOf(false) }
    var pinging by remember { mutableStateOf(false) }
    var pingResult by remember { mutableStateOf<String?>(null) }
    var pushing by remember { mutableStateOf(false) }
    var pushResult by remember { mutableStateOf<String?>(null) }
    var pushDetail by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(CredFilter.ALL) }

    // 排序：标注优先，其次按入库时间降序
    val sorted = remember(credentials) {
        credentials.sortedWith(compareByDescending<BuddyCredential> { !it.note.isNullOrEmpty() }
            .thenByDescending { it.savedAt })
    }
    val filtered = remember(sorted, filter) { sorted.filter { filter.matches(it) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        ScreenTitle("凭证")
        Spacer(Modifier.height(8.dp))

        // 刷新卡
        ActionCard(
            icon = "🔄",
            title = if (refreshing) "刷新中…" else "立即刷新全部 Token",
            color = AppColors.brand,
            onClick = {
                refreshing = true
                CredentialStore.refreshAllIfNeeded(0)
                scope.launch {
                    kotlinx.coroutines.delay(1500)
                    refreshing = false
                }
            }
        )
        Spacer(Modifier.height(10.dp))

        // 批量测活卡
        ActionCard(
            icon = "🩺",
            title = if (pinging) "测活中…" else "批量测活全部凭证",
            color = AppColors.warning,
            trailing = if (!pinging) pingResult else null,
            enabled = credentials.isNotEmpty(),
            onClick = {
                pinging = true
                pingResult = null
                CredentialStore.pingAll(onDone = { ok, risk, dead ->
                    pinging = false
                    pingResult = "✅ $ok · ⚠️ $risk · ❌ $dead"
                })
            }
        )
        Spacer(Modifier.height(10.dp))

        // 推送卡
        ActionCard(
            icon = "📤",
            title = if (pushing) "推送中…" else "推送全部凭证到服务器",
            color = AppColors.success,
            trailing = if (!pushing) pushResult else null,
            enabled = Prefs.pushBaseURL.value.isNotBlank() && credentials.isNotEmpty(),
            onClick = {
                pushing = true
                pushResult = "推送中…"
                pushDetail = null
                scope.launch {
                    val (ok, failed) = CredentialStore.pushAll()
                    pushing = false
                    pushResult = if (failed.isEmpty()) "✅ 成功 $ok 个" else "✅ 成功 $ok 个 · ❌ 失败 ${failed.size} 个"
                    pushDetail = failed.joinToString("\n").ifEmpty { null }
                }
            }
        )

        // 推送失败详情
        pushDetail?.takeIf { it.isNotEmpty() }?.let { d ->
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.danger.copy(alpha = 0.06f))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚠️ 推送失败详情", color = AppColors.danger, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "复制",
                        color = AppColors.danger,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(AppColors.danger.copy(alpha = 0.1f))
                            .clickable { copyToClipboard(d) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(d, fontSize = 11.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(Modifier.height(16.dp))

        // 凭证列表标题 + 筛选
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("已入库凭证", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            SegmentedSelector(
                options = CredFilter.values().map { it.label to it.name },
                selected = filter.name,
                onSelect = { key -> filter = CredFilter.valueOf(key) }
            )
        }
        Spacer(Modifier.height(6.dp))
        Text("${filtered.size} 个", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(10.dp))

        when {
            credentials.isEmpty() -> GlassCard {
                EmptyState("🔑", "暂无凭证", "先在「授权」页创建链接并完成登录\n凭证会自动入库")
            }
            filtered.isEmpty() -> GlassCard {
                EmptyState("🔍", "无匹配凭证", "当前筛选「${filter.label}」下没有凭证\n可切换筛选或先批量测活")
            }
            else -> {
                filtered.forEach { cred ->
                    CredentialRow(cred, onOpen = { onOpenCred(cred.account.uid) })
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ActionCard(
    icon: String,
    title: String,
    color: Color,
    onClick: () -> Unit,
    trailing: String? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppColors.cornerRadius.dp))
            .background(if (enabled) color.copy(alpha = 0.08f) else Color.Gray.copy(alpha = 0.06f))
            .clickable(enabled = enabled) { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 15.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = if (enabled) color else Color.Gray,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(trailing, fontSize = 12.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Text("›", fontSize = 14.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun CredentialRow(cred: BuddyCredential, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppColors.cornerRadius.dp))
            .background(CardBackground)
            .clickable { onOpen() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(AppColors.brandGradient),
            contentAlignment = Alignment.Center
        ) {
            Text(cred.initial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cred.displayName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (cred.subInfo.isNotEmpty()) {
                    Text(cred.subInfo, fontSize = 12.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.weight(1f))
                when {
                    cred.riskControlled == true -> PillBadge(AppColors.warning, "⚠️ 风控")
                    cred.alive == true -> PillBadge(AppColors.success, "✓ 有效")
                    cred.alive == false -> PillBadge(AppColors.danger, "✕ 失效")
                }
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (cred.balance >= 0) {
                    Text("💳 ${cred.balance} 积分", fontSize = 11.sp, color = AppColors.success)
                    Spacer(Modifier.width(10.dp))
                }
                Text("🕐 ${fmtDateTime(cred.savedAt)}", fontSize = 11.sp, color = Color.Gray)
            }
            Spacer(Modifier.height(2.dp))
            Text("🌐 ${cred.auth.domain}", fontSize = 11.sp, color = Color.Gray)
        }
    }
}

// MARK: - 凭证详情页

@Composable
fun CredentialDetailScreen(cred: BuddyCredential, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var copiedField by remember { mutableStateOf<String?>(null) }
    var pingResult by remember { mutableStateOf<String?>(null) }
    var balanceResult by remember { mutableStateOf<String?>(null) }
    var balancePackages by remember { mutableStateOf<List<ResourcePackage>>(emptyList()) }
    var pushResult by remember { mutableStateOf<String?>(null) }
    var showDelete by remember { mutableStateOf(false) }
    var showNote by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf(cred.note ?: "") }

    LaunchedEffect(copiedField) {
        if (copiedField != null) {
            kotlinx.coroutines.delay(1500)
            copiedField = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 顶部栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("‹ 返回", color = AppColors.brand, fontSize = 16.sp, modifier = Modifier.clickable { onBack() }.padding(4.dp))
            Spacer(Modifier.width(8.dp))
            Text("凭证详情", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // 头像卡
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardBackground)
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(72.dp).clip(CircleShape).background(AppColors.brandGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Text(cred.initial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                }
                Spacer(Modifier.height(10.dp))
                Text(cred.displayName, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🌐", fontSize = 11.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(cred.auth.domain, fontSize = 12.sp, color = Color.Gray)
                }
            }
            Spacer(Modifier.height(16.dp))

            // 账号信息卡
            GlassCard {
                Text("账号信息", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("👤", "UID", cred.account.uid, Modifier.weight(1f))
                    MetricCard("📅", "过期时间", fmtDateTime(cred.auth.expiresAt), Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (cred.account.enterpriseId.isNotEmpty()) {
                        MetricCard("🏢", "Enterprise", cred.account.enterpriseId, Modifier.weight(1f))
                    }
                    MetricCard("🌐", "区域", cred.auth.domain, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(16.dp))

            // 凭证卡（点击复制）
            GlassCard {
                Text("凭证（点击复制）", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                CopyCard("Access Token", cred.auth.accessToken, copiedField == "Access Token") {
                    copyToClipboard(cred.auth.accessToken); copiedField = "Access Token"
                }
                Spacer(Modifier.height(10.dp))
                CopyCard("Refresh Token", cred.auth.refreshToken, copiedField == "Refresh Token") {
                    copyToClipboard(cred.auth.refreshToken); copiedField = "Refresh Token"
                }
            }
            Spacer(Modifier.height(16.dp))

            // 操作卡
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OpButton("🩺", "测活（验证 Token 有效）", AppColors.brand, pingResult) {
                    pingResult = "检测中…"
                    CredentialStore.pingCredential(cred) { alive, _ ->
                        pingResult = if (alive) "✅ 凭证有效" else "❌ 凭证失效"
                    }
                }
                OpButton("💳", "余额查询（套餐）", AppColors.success, balanceResult) {
                    balanceResult = "查询中…"
                    scope.launch {
                        val (total, pkgs) = CredentialStore.queryResource(cred)
                        balancePackages = pkgs
                        balanceResult = if (pkgs.isEmpty()) "未查询到套餐" else "💰 剩余 $total 积分 · ${pkgs.size} 个套餐"
                    }
                }
                if (balancePackages.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SecondaryBackground)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        balancePackages.forEach { pkg -> PackageRow(pkg) }
                    }
                }
                OpButton("📤", "推送到服务器", AppColors.success, pushResult) {
                    pushResult = "推送中…"
                    scope.launch {
                        pushResult = runCatching {
                            val (uid, nick) = CredentialStore.pushCredential(cred)
                            "✅ 已推送 ${nick.ifEmpty { uid }}"
                        }.getOrElse { "❌ 推送失败: ${it.message}" }
                    }
                }
                OpButton("🗒", "标注", AppColors.brand, null) { showNote = true }
                OpButton("📄", "导出凭证（复制 JSON）", AppColors.brand, null) {
                    exportCredential(cred)
                    copiedField = "export"
                }
                OpButton("🗑", "删除凭证", AppColors.danger, null) { showDelete = true }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // 删除确认
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除凭证") },
            text = { Text("删除「${cred.displayName}」？此操作不可恢复") },
            confirmButton = {
                TextButton(onClick = {
                    CredentialStore.deleteCredential(cred)
                    showDelete = false
                    onBack()
                }) { Text("删除", color = AppColors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("取消") }
            }
        )
    }

    // 标注对话框
    if (showNote) {
        AlertDialog(
            onDismissRequest = { showNote = false },
            title = { Text("标注凭证") },
            text = {
                Column {
                    TextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        placeholder = { Text("备注（如：买的号1）") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = AppColors.brand,
                            unfocusedIndicatorColor = Color.Gray
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("标注后列表优先展示该凭证", fontSize = 11.sp, color = Color.Gray)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    CredentialStore.setNote(cred.account.uid, noteText)
                    showNote = false
                }) { Text("保存", color = AppColors.brand) }
            },
            dismissButton = {
                TextButton(onClick = {
                    CredentialStore.setNote(cred.account.uid, "")
                    showNote = false
                }) { Text("清除标注", color = AppColors.danger) }
            }
        )
    }
}

@Composable
private fun CopyCard(label: String, value: String, copied: Boolean, onCopy: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SecondaryBackground)
            .clickable { onCopy() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 11.sp, color = Color.Gray)
            Spacer(Modifier.height(3.dp))
            Text(value, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (copied) AppColors.success.copy(alpha = 0.15f) else AppColors.brand.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Text(if (copied) "✓" else "⧉", color = if (copied) AppColors.success else AppColors.brand, fontSize = 13.sp)
        }
    }
}

@Composable
private fun OpButton(icon: String, title: String, color: Color, result: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.08f))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 15.sp)
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = color, modifier = Modifier.weight(1f))
        if (result != null) {
            Text(result, fontSize = 12.sp, color = if (result.startsWith("✅") || result.contains("💰")) AppColors.success else if (result == "查询中…" || result == "检测中…" || result == "推送中…") AppColors.warning else AppColors.danger, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Text("›", fontSize = 14.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun PackageRow(pkg: ResourcePackage) {
    val hasCycle = pkg.cycleSize > 0 || pkg.cycleUsed > 0 || pkg.cycleRemain > 0
    val total = maxOf(if (hasCycle) pkg.cycleSize else pkg.capacitySize, 1)
    val remain = maxOf(if (hasCycle) pkg.cycleRemain else pkg.capacityRemain, 0)
    val used = maxOf(if (hasCycle) pkg.cycleUsed else pkg.capacityUsed, 0)
    val ratio = used.toDouble() / total.toDouble()
    val color = if (ratio > 0.85) AppColors.danger else if (ratio > 0.6) AppColors.warning else AppColors.success

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(pkg.packageName, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("剩余 $remain", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { ratio.toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = color,
            trackColor = Color.Gray.copy(alpha = 0.2f)
        )
        Spacer(Modifier.height(4.dp))
        Row {
            Text("已用 $used", fontSize = 10.sp, color = Color.Gray)
            Spacer(Modifier.weight(1f))
            Text("总额 $total", fontSize = 10.sp, color = Color.Gray)
        }
        if (hasCycle && pkg.capacitySize != pkg.cycleSize) {
            Spacer(Modifier.height(2.dp))
            Text("周期内 ${pkg.cycleRemain}/${pkg.cycleSize} · 总 ${pkg.capacityRemain}/${pkg.capacitySize}", fontSize = 9.sp, color = Color.Gray)
        }
    }
}

private fun exportCredential(cred: BuddyCredential) {
    val doc = JSONObject().apply {
        put("account", JSONObject().apply {
            put("uid", cred.account.uid)
            put("enterpriseId", cred.account.enterpriseId)
            put("nickname", cred.account.nickname)
        })
        put("auth", JSONObject().apply {
            put("accessToken", cred.auth.accessToken)
            put("refreshToken", cred.auth.refreshToken)
            put("expiresAt", cred.auth.expiresAt)
            put("domain", cred.auth.domain)
        })
    }
    copyToClipboard(doc.toString(2))
    CredentialStore.appendLog("📄 已导出凭证 JSON（已复制）: ${cred.account.nickname}")
}
