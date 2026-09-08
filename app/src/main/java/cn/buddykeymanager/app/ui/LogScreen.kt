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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.buddykeymanager.app.store.CredentialStore
import cn.buddykeymanager.app.theme.AppColors

@Composable
fun LogScreen() {
    val log by CredentialStore.log.collectAsState()
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        ScreenTitle("日志")

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("操作日志", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text(
                if (copied) "已复制" else "复制",
                color = if (copied) AppColors.success else AppColors.brand,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(AppColors.brand.copy(alpha = 0.1f))
                    .clickable {
                        copyToClipboard(log)
                        copied = true
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "清空",
                color = AppColors.danger,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(AppColors.danger.copy(alpha = 0.1f))
                    .clickable { CredentialStore.log.value = "" }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        Spacer(Modifier.height(12.dp))

        GlassCard(padding = 12.dp) {
            if (log.isEmpty()) {
                Text(
                    "暂无日志",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp)
                )
            } else {
                Text(
                    log,
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 17.sp
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
