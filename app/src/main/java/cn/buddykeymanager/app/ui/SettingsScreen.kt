package cn.buddykeymanager.app.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.buddykeymanager.app.store.Prefs
import cn.buddykeymanager.app.store.SmsStore
import cn.buddykeymanager.app.theme.AppColors
import cn.buddykeymanager.app.theme.SecondaryBackground

@Composable
fun SettingsScreen() {
    val provider by Prefs.smsProvider.collectAsState()
    val token by Prefs.smsToken.collectAsState()
    val cardKey by Prefs.smsCardKey.collectAsState()
    val keyword by Prefs.smsKeyword.collectAsState()
    val cardType by Prefs.smsCardType.collectAsState()
    val pushBaseURL by Prefs.pushBaseURL.collectAsState()
    val pushPassword by Prefs.pushPassword.collectAsState()
    val balance by SmsStore.balance.collectAsState()
    val balanceUpdatedAt by SmsStore.balanceUpdatedAt.collectAsState()

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
                Text("v1.0", fontSize = 11.sp, color = AppColors.brand, modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(AppColors.brand.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text("WorkBuddy / CodeBuddy OAuth 密钥管理器 · 无痕登录 / 接码 / 凭证管理", fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(12.dp))
            Text(
                "查看更新 · 下载最新版",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.brandGradient)
                    .clickable { openReleasePage() }
                    .padding(vertical = 14.dp)
                    .let { it },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        Spacer(Modifier.height(24.dp))
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

private fun openReleasePage() {
    runCatching {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Admin6016/BuddyKeyManager/releases/latest"))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        Prefs.context().startActivity(i)
    }
}
