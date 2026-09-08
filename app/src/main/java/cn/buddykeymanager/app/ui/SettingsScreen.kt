package cn.buddykeymanager.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.buddykeymanager.app.BuildConfig
import cn.buddykeymanager.app.net.ReleaseInfo
import cn.buddykeymanager.app.net.UpdateResult
import cn.buddykeymanager.app.net.Updater
import cn.buddykeymanager.app.store.Prefs
import cn.buddykeymanager.app.store.SmsStore
import cn.buddykeymanager.app.theme.AppColors
import cn.buddykeymanager.app.theme.SecondaryBackground
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val provider by Prefs.smsProvider.collectAsState()
    val token by Prefs.smsToken.collectAsState()
    val cardKey by Prefs.smsCardKey.collectAsState()
    val keyword by Prefs.smsKeyword.collectAsState()
    val cardType by Prefs.smsCardType.collectAsState()
    val pushBaseURL by Prefs.pushBaseURL.collectAsState()
    val pushPassword by Prefs.pushPassword.collectAsState()
    val githubToken by Prefs.githubToken.collectAsState()
    val balance by SmsStore.balance.collectAsState()
    val balanceUpdatedAt by SmsStore.balanceUpdatedAt.collectAsState()

    var checking by remember { mutableStateOf(false) }
    var updateRelease by remember { mutableStateOf<ReleaseInfo?>(null) }
    var updateMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun doCheckUpdate() {
        if (checking) return
        checking = true
        scope.launch {
            val r = Updater.check(BuildConfig.VERSION_NAME)
            checking = false
            when (r) {
                is UpdateResult.Available -> updateRelease = r.release
                is UpdateResult.UpToDate -> updateMsg = "已是最新版本 v${BuildConfig.VERSION_NAME}"
                is UpdateResult.Failed -> updateMsg = r.message
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        ScreenTitle("设置")

        // 接码设置卡
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📱 接码设置", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                Text(if (provider == "workbuddy") "workbuddy 卡密" else "ejiema 接码", fontSize = 11.sp, color = Color.Gray)
            }
            Spacer(Modifier.height(12.dp))

            SegmentedSelector(
                options = listOf("ejiema 接码" to "ejiema", "workbuddy 卡密" to "workbuddy"),
                selected = provider,
                onSelect = { Prefs.setSmsProvider(it) }
            )
            Spacer(Modifier.height(12.dp))

            if (provider == "ejiema") {
                LabeledField("ejiema API Token", token, "填写接码平台 Token") { Prefs.setSmsToken(it) }
                Spacer(Modifier.height(12.dp))

                // 余额
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SecondaryBackground)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("💳 余额", fontSize = 13.sp, color = Color.Gray)
                    Spacer(Modifier.weight(1f))
                    Text(
                        balance,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (balance == "—" || balance == "查询失败") Color.Gray else AppColors.success
                    )
                    if (balanceUpdatedAt.isNotEmpty() && balance != "查询失败") {
                        Spacer(Modifier.width(4.dp))
                        Text("($balanceUpdatedAt)", fontSize = 10.sp, color = Color.Gray)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "↻",
                        color = AppColors.brand,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppColors.brand.copy(alpha = 0.1f))
                            .clickable { SmsStore.queryBalance() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))

                LabeledField("短信关键词", keyword, "默认：腾讯") { Prefs.setSmsKeyword(it) }
                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("卡类型", fontSize = 13.sp, color = Color.Gray)
                    Spacer(Modifier.weight(1f))
                    SegmentedSelector(
                        options = listOf("全部" to "全部", "实卡" to "实卡", "虚卡" to "虚卡"),
                        selected = cardType,
                        onSelect = { Prefs.setSmsCardType(it) }
                    )
                }
            } else {
                LabeledField("workbuddy 卡密", cardKey, "填写接码平台卡密") { Prefs.setSmsCardKey(it) }
                Spacer(Modifier.height(8.dp))
                Text("浏览器快捷栏输入接码网址，自动取号/取码/填号/登录", fontSize = 11.sp, color = Color.Gray)
            }
        }
        Spacer(Modifier.height(16.dp))

        // 服务器推送设置卡
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🖥 服务器推送", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                Text("Buddy2API", fontSize = 11.sp, color = Color.Gray)
            }
            Spacer(Modifier.height(12.dp))
            LabeledField("服务器地址", pushBaseURL, "如 http://YOUR_SERVER_IP:10082") { Prefs.setPushBaseURL(it) }
            Spacer(Modifier.height(12.dp))
            LabeledField("管理密码", pushPassword, "填写服务器管理密码", password = true) { Prefs.setPushPassword(it) }
            Spacer(Modifier.height(8.dp))
            Text("在「凭证」页点击推送，把本地凭证一键入库到 Buddy2API 服务器", fontSize = 11.sp, color = Color.Gray)
        }
        Spacer(Modifier.height(16.dp))

        // 关于卡
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔐 BuddyKeyManager", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "v${BuildConfig.VERSION_NAME}",
                    fontSize = 11.sp,
                    color = AppColors.brand,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(AppColors.brand.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("WorkBuddy / CodeBuddy OAuth 密钥管理器 · 无痕登录 / 接码 / 凭证管理", fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(12.dp))

            BrandButton(
                text = if (checking) "检查中…" else "检查更新",
                enabled = !checking,
                onClick = { doCheckUpdate() }
            )
            Spacer(Modifier.height(8.dp))

            Text(
                "GitHub 仓库 · 手动下载",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.brandGradient)
                    .clickable { openUrl("https://github.com/turbomind66/BuddyKeyManager-Android/releases/latest") }
                    .padding(vertical = 14.dp),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))

            LabeledField("GitHub Token（私有仓库检查更新用）", githubToken, "可选：填入只读 Token", password = true) { Prefs.setGithubToken(it) }
            Spacer(Modifier.height(4.dp))
            Text("仓库为私有，检查更新需配置只读 Token；留空则尝试公开访问", fontSize = 11.sp, color = Color.Gray)
        }
        Spacer(Modifier.height(24.dp))
    }

    // 发现新版本弹窗
    updateRelease?.let { info ->
        UpdateDialog(
            version = info.tagName,
            notes = info.body,
            url = if (info.htmlUrl.isNotEmpty()) info.htmlUrl else "https://github.com/turbomind66/BuddyKeyManager-Android/releases/latest",
            onDismiss = { updateRelease = null }
        )
    }

    // 检查结果提示弹窗（已最新 / 失败）
    updateMsg?.let { msg ->
        AlertDialog(
            onDismissRequest = { updateMsg = null },
            title = { Text("检查更新") },
            text = { Text(msg, fontSize = 13.sp, color = Color.Gray) },
            confirmButton = {
                TextButton(onClick = { updateMsg = null }) { Text("知道了", color = AppColors.brand) }
            }
        )
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    placeholder: String,
    password: Boolean = false,
    onChange: (String) -> Unit
) {
    Column {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(6.dp))
        TextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text(placeholder, fontSize = 13.sp) },
            singleLine = true,
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SecondaryBackground,
                unfocusedContainerColor = SecondaryBackground,
                focusedIndicatorColor = AppColors.brand,
                unfocusedIndicatorColor = Color.Transparent
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
