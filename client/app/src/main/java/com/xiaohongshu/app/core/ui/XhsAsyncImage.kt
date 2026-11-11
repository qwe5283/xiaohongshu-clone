package com.xiaohongshu.app.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.xiaohongshu.app.R
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.image.ImageLoader

/** 由 MainActivity 注入。 */
val LocalImageLoader = staticCompositionLocalOf<ImageLoader> {
    error("LocalImageLoader 未提供：请在根布局用 CompositionLocalProvider 注入 ImageLoader")
}

/**
 * 异步图片。
 *
 * @param targetWidthDp/targetHeightDp 期望显示尺寸，用于下采样；不传则按 1080px 封顶。
 * @param fallbackPainter 加载中、URL 为空或加载失败时的兜底图；不传时保留容器本身。
 */
@Composable
fun XhsAsyncImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    targetWidthDp: Dp? = null,
    targetHeightDp: Dp? = null,
    clip: Shape? = null,
    alignment: Alignment = Alignment.Center,
    fallbackPainter: Painter? = null,
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

    Box(modifier = base, contentAlignment = alignment) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = null,
                contentScale = contentScale,
                alignment = alignment,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (fallbackPainter != null) {
            Image(
                painter = fallbackPainter,
                contentDescription = null,
                contentScale = contentScale,
                alignment = alignment,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * 圆形头像。头像档：18 / 24 / 36 / 48 / 108（取值见 [Dimens]）。
 * 加载中、URL 为空或加载失败时显示 `ic_avatar_fallback`。
 *
 * @param borderWidthPx 描边宽度，单位为**物理像素**：发丝线传 1f 即可（1dp 在
 *   高密度屏约合 3px，视觉过粗）；0 表示不描边。描边画在头像外沿、贴着裁剪圆，
 *   可见宽度即该值，且不改变组件占位尺寸。
 * @param borderColor 描边颜色，仅 [borderWidthPx] > 0 时生效。
 */
@Composable
fun XhsAvatar(
    url: String,
    size: Dp,
    modifier: Modifier = Modifier,
    borderWidthPx: Float = Dimens.avatarBorderWidthPx,
    borderColor: Color = XhsColor.DotInactive,
) {
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                // 矩形四边外扩半线宽，使整条描边落在裁剪圆之外：
                // 既不被不透明图片盖住，可见宽度也恰好等于 borderWidthPx
                if (borderWidthPx > 0f) {
                    val half = borderWidthPx / 2f
                    // this.size 是 DrawScope 的节点尺寸（px）；裸写 size 会被外层 Dp 形参遮蔽
                    val side = this.size.width
                    drawRoundRect(
                        color = borderColor,
                        topLeft = Offset(-half, -half),
                        size = Size(side + borderWidthPx, side + borderWidthPx),
                        cornerRadius = CornerRadius((side + borderWidthPx) / 2f),
                        style = Stroke(width = borderWidthPx),
                    )
                }
            },
    ) {
        XhsAsyncImage(
            url = url,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
            targetWidthDp = size,
            targetHeightDp = size,
            clip = CircleShape,
            fallbackPainter = painterResource(R.drawable.ic_avatar_fallback),
        )
    }
}
