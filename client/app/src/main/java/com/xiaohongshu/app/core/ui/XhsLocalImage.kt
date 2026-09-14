package com.xiaohongshu.app.core.ui

import android.content.Context
import android.graphics.ImageDecoder
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 渲染**本地**媒体：文件绝对路径或 `content://` URI。
 *
 * 分工约定（避免踩坑）：
 * - 远端 URL → [XhsAsyncImage]（走 OkHttp 缓存）；
 * - 本地文件 / 相册 `content://` → **本组件**（走 `ImageDecoder`，不需要网络）。
 *
 * 存在理由：发布流程（E2 缩略图、E3 配图）与 F3 头像/背景图的「即时预览」都必须在**上传前**
 * 就把本地图显示出来，而 [XhsAsyncImage] 只认 http(s)，对本地路径会静默失败。
 * 加载中为浅灰底，解码失败回落统一占位素材（§4.5）。
 */
@Composable
fun XhsLocalImage(
    reference: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    clip: Shape? = RoundedCornerShape(Dimens.radiusCard),
    alignment: Alignment = Alignment.Center,
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = reference) {
        value = if (reference.isBlank()) null else decodeLocal(context, reference)
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
        } else {
            Icon(
                painter = painterResource(PlaceholderIconRes),
                contentDescription = null,
                tint = XhsColor.Text3,
                modifier = Modifier.fillMaxSize(0.4f),
            )
        }
    }
}

/** 解码到约 [targetPx] 长边，避免相册原图（可能 4000px+）占满内存。 */
private suspend fun decodeLocal(context: Context, reference: String): ImageBitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            val source = if (reference.startsWith("content://")) {
                ImageDecoder.createSource(context.contentResolver, android.net.Uri.parse(reference))
            } else {
                val file = File(reference)
                if (!file.exists()) return@runCatching null
                ImageDecoder.createSource(file)
            }
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > DECODE_MAX_PX) {
                    decoder.setTargetSampleSize(
                        Integer.highestOneBit(longest / DECODE_MAX_PX).coerceAtLeast(1),
                    )
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }.asImageBitmap()
        }.getOrNull()
    }

/** 长边解码上限（缩略图/预览场景足够）。 */
private const val DECODE_MAX_PX = 1080

/**
 * 统一媒体入口：自动按引用类型分发到远端或本地加载器。
 * 需要「同一个位置既可能显示远端 URL 又可能显示本地待上传文件」时用它（E2/F3 都用得到）。
 */
@Composable
fun XhsMediaImage(
    reference: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    clip: Shape? = RoundedCornerShape(Dimens.radiusCard),
) {
    if (reference.startsWith("http://") || reference.startsWith("https://")) {
        XhsAsyncImage(
            url = reference,
            modifier = modifier,
            contentScale = contentScale,
            clip = clip,
            showGlyphOnFailure = false,
        )
    } else {
        XhsLocalImage(
            reference = reference,
            modifier = modifier,
            contentScale = contentScale,
            clip = clip,
        )
    }
}

/** 方形圆角缩略图（E2 媒体条、F3 头像/背景图预览）。 */
@Composable
fun XhsMediaThumb(
    reference: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    XhsMediaImage(
        reference = reference,
        modifier = modifier.size(size),
        clip = RoundedCornerShape(4.dp),
    )
}
