package cn.buddykeymanager.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

/** 品牌色（绿色系），与 iOS 版 Theme.swift 保持一致 */
object AppColors {
    val brand = Color(0xFF0FB882)          // 主品牌色（翠绿）
    val brandPurple = Color(0xFF33E89E)    // 次品牌色（薄荷绿）
    val success = Color(0xFF0FB882)
    val warning = Color(0xFFF29933)
    val danger = Color(0xFFEB5757)

    val brandGradient: Brush = Brush.linearGradient(listOf(brand, brandPurple))

    /** 禁用态背景（灰色 Brush，避免与品牌渐变类型不一致） */
    val disabledBrush: Brush = SolidColor(Color(0x33888888))

    // 深色模式下的表面色（iOS 的 secondarySystemGroupedBackground 对应）
    val surfaceDark = Color(0xFF1C1C1E)
    val surfaceDark2 = Color(0xFF2C2C2E)
    val surfaceDark3 = Color(0xFF3A3A3C)

    val surfaceLight = Color(0xFFF2F2F7)
    val surfaceLight2 = Color(0xFFFFFFFF)
    val surfaceLight3 = Color(0xFFE5E5EA)

    val cornerRadius = 16
}

private val LightColors = lightColorScheme(
    primary = AppColors.brand,
    onPrimary = Color.White,
    secondary = AppColors.brandPurple,
    background = AppColors.surfaceLight,
    surface = AppColors.surfaceLight2,
    error = AppColors.danger,
)

private val DarkColors = darkColorScheme(
    primary = AppColors.brand,
    onPrimary = Color.White,
    secondary = AppColors.brandPurple,
    background = Color(0xFF000000),
    surface = AppColors.surfaceDark2,
    error = AppColors.danger,
)

/** 卡片背景（随主题自适应） */
val CardBackground: Color
    @Composable get() = if (isSystemInDarkTheme()) AppColors.surfaceDark2 else AppColors.surfaceLight2

/** 次级卡片背景 */
val SecondaryBackground: Color
    @Composable get() = if (isSystemInDarkTheme()) AppColors.surfaceDark3 else AppColors.surfaceLight3

/** 页面背景 */
val PageBackground: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF000000) else AppColors.surfaceLight

@Composable
fun BuddyTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
