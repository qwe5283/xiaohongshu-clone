package com.xiaohongshu.app.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaohongshu.app.core.design.XhsColor

/**
 * 用 Canvas 直接绘制的少量基础字形。
 *
 * 为什么不都用占位素材：＋ 按钮（底部 Tab 正中的主操作）、☰ 抽屉入口、▶ 视频角标、
 * 协议勾选圆圈属于**结构性控件**，用占位素材会让产品看起来是坏的。这几个形状简单到
 * 可以精确绘制，因此就地画；其余真正缺失的图标一律沿用统一占位素材（见 [PlaceholderIconRes]）。
 */
private const val DEFAULT_STROKE_RATIO = 0.1f

/** ＋（底部 Tab 发布按钮、多图添加）。 */
@Composable
fun XhsPlusGlyph(
    size: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = size * DEFAULT_STROKE_RATIO,
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = strokeWidth.toPx()
        val w = size.toPx()
        val inset = stroke
        val cap = StrokeCap.Round
        drawLine(
            color = color,
            start = Offset(inset, w / 2f),
            end = Offset(w - inset, w / 2f),
            strokeWidth = stroke,
            cap = cap,
        )
        drawLine(
            color = color,
            start = Offset(w / 2f, inset),
            end = Offset(w / 2f, w - inset),
            strokeWidth = stroke,
            cap = cap,
        )
    }
}

/** ☰ 抽屉入口（仅「我」页左上角使用，线框 F1/F4）。 */
@Composable
fun XhsMenuGlyph(
    size: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = size * DEFAULT_STROKE_RATIO,
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = strokeWidth.toPx()
        val w = size.toPx()
        val cap = StrokeCap.Round
        listOf(0.22f, 0.5f, 0.78f).forEach { fraction ->
            drawLine(
                color = color,
                start = Offset(stroke, w * fraction),
                end = Offset(w - stroke, w * fraction),
                strokeWidth = stroke,
                cap = cap,
            )
        }
    }
}

/** ▶ 视频角标（瀑布流卡片右上 20×20）。 */
@Composable
fun XhsPlayGlyph(
    size: Dp,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = size.toPx()
        val path = Path().apply {
            moveTo(w * 0.30f, w * 0.18f)
            lineTo(w * 0.82f, w * 0.50f)
            lineTo(w * 0.30f, w * 0.82f)
            close()
        }
        drawPath(path, color)
    }
}

/**
 * 协议勾选圆圈（A3/A4「我已阅读并同意」）。
 * [checked] 为 true 时填充主色并画白色对勾。
 */
@Composable
fun XhsCheckCircle(
    checked: Boolean,
    size: Dp = 14.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = size.toPx()
        val radius = w / 2f - w * 0.07f
        if (checked) {
            drawCircle(color = XhsColor.Red, radius = radius, center = Offset(w / 2f, w / 2f))
            val path = Path().apply {
                moveTo(w * 0.28f, w * 0.52f)
                lineTo(w * 0.44f, w * 0.68f)
                lineTo(w * 0.74f, w * 0.34f)
            }
            drawPath(
                path = path,
                color = Color.White,
                style = Stroke(width = w * 0.11f, cap = StrokeCap.Round),
            )
        } else {
            drawCircle(
                color = XhsColor.Text2,
                radius = radius,
                center = Offset(w / 2f, w / 2f),
                style = Stroke(width = w * 0.08f),
            )
        }
    }
}

/** 空态插画位：一个虚线框 + 占位素材，明确表达「待替换矢量插画」。 */
@Composable
fun XhsIllustrationSlot(
    modifier: Modifier = Modifier,
    width: Dp = 120.dp,
    height: Dp = 88.dp,
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier.size(width, height),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            painter = androidx.compose.ui.res.painterResource(PlaceholderIllustrationRes),
            contentDescription = null,
            tint = XhsColor.Text3,
            modifier = Modifier.size(width * 0.66f, height * 0.66f),
        )
    }
}

/**
 * 人形字形（底 Tab「我」、他人主页入口）。
 *
 * 这是底部导航的结构性图标，用占位素材会让主导航看起来是坏的，故就地绘制；
 * [filled] 为选中态（线框底 Tab 选中为加粗/实心）。
 */
@Composable
fun XhsPersonGlyph(
    size: Dp,
    color: Color,
    filled: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = size.toPx()
        val headRadius = w * 0.185f
        val headCenter = Offset(w / 2f, w * 0.285f)

        if (filled) {
            drawCircle(color = color, radius = headRadius, center = headCenter)
        } else {
            drawCircle(
                color = color,
                radius = headRadius,
                center = headCenter,
                style = Stroke(width = w * 0.095f),
            )
        }

        // 肩部：上凸半椭圆
        val bodyWidth = w * 0.56f
        val bodyHeight = w * 0.46f
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = filled,
            topLeft = Offset((w - bodyWidth) / 2f, w * 0.56f),
            size = androidx.compose.ui.geometry.Size(bodyWidth, bodyHeight),
            style = if (filled) androidx.compose.ui.graphics.drawscope.Fill
            else Stroke(width = w * 0.095f),
        )
    }
}
