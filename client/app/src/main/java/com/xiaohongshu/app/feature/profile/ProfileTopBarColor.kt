package com.xiaohongshu.app.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.compositeOver
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.ui.LocalImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun rememberProfileTopBarColor(backgroundUrl: String): Color = key(backgroundUrl) {
    val loader = LocalImageLoader.current
    val fallback = XhsColor.ProfileHeaderScrim.compositeOver(XhsColor.PlaceholderBg)
    val color by produceState(fallback, backgroundUrl, loader) {
        // 与资料头使用同一解码尺寸，复用图片缓存和在途请求（契约 §12.8）。
        val image = loader.load(backgroundUrl) ?: return@produceState
        value = withContext(Dispatchers.Default) {
            val bitmap = image.asAndroidBitmap()
            var red = 0L
            var green = 0L
            var blue = 0L
            var alpha = 0L
            val stepX = (bitmap.width / COLOR_SAMPLE_GRID).coerceAtLeast(1)
            val stepY = (bitmap.height / COLOR_SAMPLE_GRID).coerceAtLeast(1)
            for (y in stepY / 2 until bitmap.height step stepY) {
                for (x in stepX / 2 until bitmap.width step stepX) {
                    val pixel = bitmap.getPixel(x, y)
                    val weight = (pixel ushr 24).toLong()
                    alpha += weight
                    red += (pixel ushr 16 and 0xff) * weight
                    green += (pixel ushr 8 and 0xff) * weight
                    blue += (pixel and 0xff) * weight
                }
            }
            if (alpha == 0L) fallback else XhsColor.ProfileHeaderScrim.compositeOver(
                Color(red = (red / alpha).toInt(), green = (green / alpha).toInt(), blue = (blue / alpha).toInt()),
            )
        }
    }
    color
}

private const val COLOR_SAMPLE_GRID = 32
