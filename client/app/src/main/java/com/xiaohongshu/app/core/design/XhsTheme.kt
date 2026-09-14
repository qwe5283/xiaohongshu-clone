package com.xiaohongshu.app.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 应用主题。
 *
 * 小红书本身是**浅色为主 + 视频详情页深色沉浸**的产品，不存在跟随系统的全局暗色模式；
 * 深色主题由视频详情页在页面内部自行提供（[LocalXhsDark]）。因此这里始终构建浅色配色，
 * 不读取 [isSystemInDarkTheme]，避免用户在系统开暗色时首页变黑（与原版行为不符）。
 */
private val XhsLightColors = lightColorScheme(
    primary = XhsColor.Red,
    onPrimary = Color.White,
    background = XhsColor.Bg,
    onBackground = XhsColor.Text1,
    surface = XhsColor.Bg,
    onSurface = XhsColor.Text1,
    surfaceVariant = XhsColor.BgGray,
    onSurfaceVariant = XhsColor.Text2,
    outline = XhsColor.Divider,
    error = XhsColor.Error,
    onError = Color.White,
)

private val XhsShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(Dimens.radiusCard),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(Dimens.radiusSheet),
    large = androidx.compose.foundation.shape.RoundedCornerShape(Dimens.radiusSheet),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(Dimens.radiusSheet),
)

/** 仅用于让 Material 组件的默认文字尺寸有合理落点；业务代码请直接用 [XhsType]。 */
private val XhsTypography = Typography(
    titleLarge = XhsType.pageTitle,
    titleMedium = XhsType.sectionTitle,
    bodyLarge = XhsType.body,
    bodyMedium = XhsType.listTitle,
    labelLarge = XhsType.buttonLabel,
    labelMedium = XhsType.meta,
)

/**
 * 页面级「深色沉浸」标记（C2 视频详情、C3 评论面板、C2-4/C2-5）。
 * 为 true 时骨架/分隔线/占位等中性色切换到深色变体。
 */
val LocalXhsDark = staticCompositionLocalOf { false }

/** 页面级遮罩是否存在（弹层打开时；用于状态栏图标反色等）。 */
val LocalXhsScrimVisible = staticCompositionLocalOf { false }

/** 当前页是否为「无底部 Tab 的推入式页面」——供内边距决策使用。 */
val LocalXhsImmersive = staticCompositionLocalOf { false }

@Composable
fun XhsTheme(
    dark: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalXhsDark provides dark,
        LocalContentColor provides if (dark) Color.White else XhsColor.Text1,
    ) {
        MaterialTheme(
            colorScheme = XhsLightColors,
            typography = XhsTypography,
            shapes = XhsShapes,
            content = content,
        )
    }
}

/** 便捷读色：随 [LocalXhsDark] 切换中性色，供深色沉浸页复用同一套组件。 */
object XhsColors {
    val divider: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalXhsDark.current) Color(0xFF2A2A2A) else XhsColor.Divider

    val bg: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalXhsDark.current) XhsColor.Black else XhsColor.Bg

    val text1: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalXhsDark.current) Color(0xFFF5F5F5) else XhsColor.Text1

    val text2: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalXhsDark.current) Color(0x99FFFFFF) else XhsColor.Text2

    /** 视频底栏区（#1A1A1A）。 */
    val barSurface: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalXhsDark.current) XhsColor.Dark else XhsColor.Bg

    /** 半透明灰胶囊底：浅色 #F5F5F5 / 深色 26% 白（视频底栏「说点什么」）。 */
    val pillSurface: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalXhsDark.current) Color(0x33FFFFFF) else XhsColor.BgGray
}

/** dp 的 0 常量，供条件内边距书写更清晰。 */
val ZeroDp: Dp = 0.dp

/** 统一给 TextStyle 换色的小工具，避免各处重复 copy。 */
fun TextStyle.on(color: Color): TextStyle = copy(color = color)
