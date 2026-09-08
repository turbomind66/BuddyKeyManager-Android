package cn.buddykeymanager.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.buddykeymanager.app.data.SessionStatus
import cn.buddykeymanager.app.theme.AppColors
import cn.buddykeymanager.app.theme.CardBackground
import cn.buddykeymanager.app.theme.SecondaryBackground
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// MARK: - 通用卡片

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    padding: androidx.compose.ui.unit.Dp = 14.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppColors.cornerRadius.dp))
            .background(CardBackground)
            .padding(padding),
        content = content
    )
}

// MARK: - 品牌渐变按钮

@Composable
fun BrandButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) AppColors.brandGradient else AppColors.disabledBrush)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        if (enabled) {
            androidx.compose.material3.Surface(
                modifier = Modifier.matchParentSize(),
                color = Color.Transparent,
                onClick = onClick
            ) {}
        }
    }
}

// MARK: - 状态徽章

@Composable
fun StatusBadge(status: SessionStatus) {
    val (color, icon, label) = when (status) {
        SessionStatus.PENDING -> Triple(AppColors.warning, "⏳", "待登录")
        SessionStatus.POLLING -> Triple(AppColors.brand, "🔄", "轮询中")
        SessionStatus.COMPLETED -> Triple(AppColors.success, "✅", "已完成")
        SessionStatus.FAILED -> Triple(AppColors.danger, "❌", "失败")
    }
    PillBadge(color = color, text = "$icon $label")
}

// MARK: - 胶囊徽章

@Composable
fun PillBadge(color: Color, text: String) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    )
}

// MARK: - 空状态

@Composable
fun EmptyState(icon: String, title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(AppColors.brand.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 30.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, fontSize = 12.sp, color = Color.Gray, lineHeight = 18.sp)
    }
}

// MARK: - 仪表卡片（凭证详情用）

@Composable
fun MetricCard(icon: String, label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SecondaryBackground)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 11.sp)
            Spacer(Modifier.width(5.dp))
            Text(label, fontSize = 11.sp, color = Color.Gray)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            value,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium
        )
    }
}

// MARK: - 页面标题

@Composable
fun ScreenTitle(title: String) {
    Text(
        title,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 12.dp)
    )
}

// MARK: - 分段选择器（通用）

@Composable
fun SegmentedSelector(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SecondaryBackground)
            .padding(3.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        options.forEach { (label, key) ->
            val active = key == selected
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) Color.White else Color.Gray,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) AppColors.brand else Color.Transparent)
                    .clickable { onSelect(key) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

// MARK: - 剪贴板

fun copyToClipboard(text: String) {
    runCatching {
        val cm = cn.buddykeymanager.app.store.Prefs.context()
            .getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("bkm", text))
    }
}

// MARK: - 时间格式化工具

fun fmtDateTime(tsSec: Long): String =
    if (tsSec <= 0) "时间未知"
    else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(tsSec * 1000))

fun fmtDateTimeMillis(ms: Long): String =
    if (ms <= 0) "时间未知"
    else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(ms))

fun fmtExpiry(tsSec: Long): String =
    if (tsSec <= 0) "—"
    else SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(tsSec * 1000))

// MARK: - 打开外部链接

fun openUrl(url: String) {
    runCatching {
        val i = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        cn.buddykeymanager.app.store.Prefs.context().startActivity(i)
    }
}

// MARK: - 更新提示对话框

@Composable
fun UpdateDialog(
    version: String,
    notes: String,
    url: String,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 $version") },
        text = {
            Column {
                Text(
                    if (notes.isBlank()) "新版本已发布，点击下方「去下载」获取更新。" else notes,
                    fontSize = 13.sp,
                    color = Color.Gray,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                onDismiss()
                openUrl(url)
            }) { Text("去下载", color = AppColors.brand, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("取消", color = Color.Gray) }
        }
    )
}
