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
 * ＋ 按钮和协议勾选圆圈属于结构性控件，形状简单，使用 Canvas 精确绘制。
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
