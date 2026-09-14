package com.xiaohongshu.app.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaohongshu.app.R
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.image.ImageLoader

/** 由 MainActivity 注入。 */
val LocalImageLoader = staticCompositionLocalOf<ImageLoader> {
    error("LocalImageLoader 未提供：请在根布局用 CompositionLocalProvider 注入 ImageLoader")
}

/** 全项目统一的缺失素材占位（图标与插画共用同一个素材）。 */
val PlaceholderIconRes = R.drawable.ic_placeholder

/** 空态插画占位。 */
val PlaceholderIllustrationRes = R.drawable.ic_placeholder_illus

/**
 * 异步图片。
 *
 * 占位策略（与产品约定一致）：所有缺失素材**统一使用同一个占位素材**，
 * 加载中为浅灰底，失败或无 URL 时叠加占位字形，便于验收时一眼看出待替换项。
 *
 * @param targetWidthDp/targetHeightDp 期望显示尺寸，用于下采样；不传则按 1080px 封顶。
 * @param showGlyphOnFailure true 时在失败占位上叠加 [PlaceholderIconRes] 字形（图标类场景用）。
 */
@Composable
fun XhsAsyncImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    targetWidthDp: Dp? = null,
    targetHeightDp: Dp? = null,
    clip: Shape? = null,
    showGlyphOnFailure: Boolean = true,
    alignment: Alignment = Alignment.Center,
) {
    val loader = LocalImageLoader.current
    val density = LocalDensity.current
    val targetW = targetWidthDp?.let { with(density) { it.roundToPx() } } ?: 0
    val targetH = targetHeightDp?.let { with(density) { it.roundToPx() } } ?: 0

    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = url,
        key2 = targetW,
        key3 = targetH,
    ) {
        value = if (url.isBlank()) null else loader.load(url, targetW, targetH)
    }

    val base = if (clip != null) modifier.clip(clip) else modifier

    Box(modifier = base.background(XhsColor.PlaceholderBg), contentAlignment = alignment) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = null,
                contentScale = contentScale,
                alignment = alignment,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (showGlyphOnFailure) {
            Icon(
                painter = painterResource(PlaceholderIconRes),
                contentDescription = null,
                tint = XhsColor.Text3,
                modifier = Modifier.fillMaxSize(0.42f),
            )
        }
    }
}

/**
 * 圆形头像。线框头像 5 档：24 / 36 / 48 / 60 / 108。
 * 缺图时用统一占位素材，不用「默认头像」第二套资源。
 */
@Composable
fun XhsAvatar(
    url: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    XhsAsyncImage(
        url = url,
        modifier = modifier.size(size),
        contentScale = ContentScale.Crop,
        targetWidthDp = size,
        targetHeightDp = size,
        clip = CircleShape,
        showGlyphOnFailure = true,
    )
}

/**
 * 带封面比例的图片框（瀑布流卡片 / 详情大图）。
 * [ratio] 为 宽/高（4:3 用 1.333、3:4 用 0.75）。
 */
@Composable
fun XhsCoverImage(
    url: String,
    ratio: Float,
    modifier: Modifier = Modifier,
    clip: Shape? = null,
    targetWidthDp: Dp? = null,
) {
    XhsAsyncImage(
        url = url,
        modifier = modifier.aspectRatio(ratio),
        contentScale = ContentScale.Crop,
        targetWidthDp = targetWidthDp,
        clip = clip,
        showGlyphOnFailure = false,
    )
}

/** 无内容时的纯色块（用于视频占位等）。 */
@Composable
fun XhsPlaceholderBox(
    modifier: Modifier = Modifier,
    color: Color = XhsColor.PlaceholderBg,
    glyphTint: Color = XhsColor.Text3,
) {
    Box(modifier = modifier.background(color), contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(PlaceholderIconRes),
            contentDescription = null,
            tint = glyphTint,
            modifier = Modifier.fillMaxSize(0.3f),
        )
    }
}
