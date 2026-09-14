package com.xiaohongshu.app.feature.publish

import android.graphics.ImageDecoder
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.xiaohongshu.app.R
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.design.XhsType
import com.xiaohongshu.app.core.publish.DraftMedia
import com.xiaohongshu.app.core.ui.PlaceholderIconRes
import com.xiaohongshu.app.core.ui.XhsAsyncImage
import com.xiaohongshu.app.core.ui.XhsCharCounter
import com.xiaohongshu.app.core.ui.XhsChevronRight
import com.xiaohongshu.app.core.ui.XhsPlayGlyph
import com.xiaohongshu.app.core.ui.XhsPlusGlyph
import com.xiaohongshu.app.core.ui.XhsSpinner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * E2 媒体缩略图边长。
 *
 * 线框 E2 明示「表单结构为占位设计，编码前需产品确认细节」，其缩略 mock 仅 40×40；
 * 但 40dp 无法承载 §4.1「触控热区一律 ≥44dp」的删除钮，故按 token 阶梯取 32×3。
 * 全部由 `Dimens` 推导，未引入新的魔法数档位。
 */
internal val MediaThumbSize: Dp = Dimens.s32 * 3

/** 线框 E2/E4 的虚线添加框描边宽度（mock 1.5px → hairline 的 2 倍）。 */
private val AddTileBorderWidth: Dp = Dimens.hairline * 2

/**
 * E2 媒体条：已选媒体缩略（可 × 删除）+ 虚线 ＋ 添加框（§7 E2）。
 *
 * 图片/视频可共存；顺序即最终入库顺序（`PublishDraft.remoteImageUrls` 按此顺序取 URL）。
 *
 * @param editable E5-1 提交中传 false：隐藏 × 与 ＋（线框 E5-1 的缩略图同样无 × / 无 ＋），
 *   同时避免上传过程中增删媒体导致 `updateMedia(index)` 写错下标。
 */
@Composable
internal fun MediaStrip(
    media: List<DraftMedia>,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    editable: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = Dimens.pagePadding),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(Dimens.s8),
    ) {
        media.forEachIndexed { index, item ->
            MediaThumb(
                item = item,
                onRemove = { onRemove(index) },
                editable = editable,
            )
        }
        if (editable) MediaAddTile(onClick = onAdd)
    }
}

/**
 * 单个媒体缩略：本地文件预览（§4.5 的 `XhsAsyncImage` 走 OkHttp，无法读 `file://`/本地路径，
 * 故本地文件在 `LocalMediaThumb` 就地解码）+ 右上 × 删除 + 视频 ▶ 角标 + 上传中/失败覆盖层。
 */
@Composable
internal fun MediaThumb(
    item: DraftMedia,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    editable: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(MediaThumbSize)
            .clip(RoundedCornerShape(Dimens.radiusCard))
            .background(XhsColor.PlaceholderBg),
    ) {
        when {
            // E3 生成配图：已是远端 URL（契约 §2.11 直接返回可入库 URL）
            item.localPath.isBlank() && item.remoteUrl.isNotBlank() -> XhsAsyncImage(
                url = item.remoteUrl,
                modifier = Modifier.fillMaxSize(),
                targetWidthDp = MediaThumbSize,
                targetHeightDp = MediaThumbSize,
                showGlyphOnFailure = false,
            )

            item.localPath.isNotBlank() -> LocalMediaThumb(
                path = item.localPath,
                size = MediaThumbSize,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (item.isVideo) {
            // 线框 E2：视频缩略用 ▶ 区分（复用结构性字形 XhsPlayGlyph）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(XhsColor.PageBadge),
                contentAlignment = Alignment.Center,
            ) {
                XhsPlayGlyph(size = Dimens.icon24, color = Color.White)
            }
        }

        // E6：逐个上传期间的逐项「上传中」态（失败项保留可重试）
        if (item.uploading || item.failed) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(XhsColor.Scrim),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (item.uploading) {
                    XhsSpinner(size = Dimens.icon20, color = Color.White)
                    Spacer(modifier = Modifier.height(Dimens.s4))
                }
                Text(
                    text = if (item.uploading) "上传中" else "上传失败",
                    style = XhsType.captionSub,
                    color = Color.White,
                )
            }
        }

        // × 删除：热区 44（§4.1），视觉为 20dp 半透明圆底 + 12dp 图标
        if (editable) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(Dimens.minTouchTarget)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(Dimens.icon20)
                        .clip(CircleShape)
                        .background(XhsColor.PageBadge),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "删除",
                        tint = Color.White,
                        modifier = Modifier.size(Dimens.icon12),
                    )
                }
            }
        }
    }
}

/** 虚线 ＋ 添加框（线框 E2：`border:1.5px dashed` + 居中 ＋）。 */
@Composable
internal fun MediaAddTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strokePx = with(LocalDensity.current) { AddTileBorderWidth.toPx() }
    val dashPx = with(LocalDensity.current) { Dimens.s8.toPx() }
    val radiusPx = with(LocalDensity.current) { Dimens.radiusCard.toPx() }
    Box(
        modifier = modifier
            .size(MediaThumbSize)
            .clip(RoundedCornerShape(Dimens.radiusCard))
            .clickable(onClick = onClick)
            .drawBehind {
                drawRoundRect(
                    color = XhsColor.Text3,
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                    style = Stroke(
                        width = strokePx,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashPx, dashPx)),
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // ＋ 属结构性控件，用 XhsPlusGlyph 就地绘制（§4.5）
        XhsPlusGlyph(size = Dimens.icon24, color = XhsColor.Text2)
    }
}

/**
 * 本地文件缩略图（相册拷贝 / 相机落盘）。
 *
 * 为什么不用 `XhsAsyncImage`：其 `ImageLoader` 只走 OkHttp（仅 http/https），
 * 本地路径与 `content://` 一律解码失败。此处用 `ImageDecoder` 按目标尺寸下采样解码，
 * 占位策略与公共组件保持一致（`PlaceholderBg` + 统一占位素材）。
 */
@Composable
private fun LocalMediaThumb(
    path: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val targetPx = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path, targetPx) {
        value = withContext(Dispatchers.IO) { decodeLocal(path, targetPx) }
    }
    Box(modifier = modifier.background(XhsColor.PlaceholderBg), contentAlignment = Alignment.Center) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = painterResource(PlaceholderIconRes),
                contentDescription = null,
                tint = XhsColor.Text3,
                modifier = Modifier.fillMaxSize(0.42f),
            )
        }
    }
}

/** 本地图片下采样解码；失败返回 null（回落占位素材）。 */
private fun decodeLocal(path: String, targetPx: Int): ImageBitmap? = runCatching {
    val source = ImageDecoder.createSource(File(path))
    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val width = info.size.width
        val height = info.size.height
        val longest = maxOf(width, height).coerceAtLeast(1)
        if (targetPx > 0 && longest > targetPx) {
            val scale = targetPx.toFloat() / longest.toFloat()
            decoder.setTargetSize(
                (width * scale).toInt().coerceAtLeast(1),
                (height * scale).toInt().coerceAtLeast(1),
            )
        }
    }.asImageBitmap()
}.getOrNull()

/**
 * E2 表单输入框（标题 / 正文）。
 *
 * 线框 E2 的表单是占位设计（`.inp` 为 1px 边框白底），这里落到设计系统既有语言：
 * 灰底（`BgGray`）圆角 8、正文 14sp、占位 `Text3`。
 *
 * @param maxLength 非空时硬截断（正文 10000）；**标题不传**——超 200 必须可达，
 *   否则 E3 预填超长标题时 E4 的「标题不能超过200个字符」将永远触发不到。
 */
@Composable
internal fun PublishField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minHeight: Dp = Dimens.minTouchTarget,
    maxLength: Int? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(minHeight)
            .clip(RoundedCornerShape(Dimens.radiusCard))
            .background(XhsColor.BgGray)
            .padding(horizontal = Dimens.s12, vertical = Dimens.s12),
    ) {
        if (value.isEmpty()) {
            Text(text = placeholder, style = XhsType.body, color = XhsColor.Text3)
        }
        BasicTextField(
            value = value,
            onValueChange = { next ->
                onValueChange(if (maxLength != null) next.take(maxLength) else next)
            },
            textStyle = XhsType.body.copy(color = XhsColor.Text1),
            cursorBrush = SolidColor(XhsColor.Text1),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** E2 字段标签（线框 `.lbl`：标题 * / 正文）。 */
@Composable
internal fun PublishFieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = XhsType.meta, color = XhsColor.Text1, modifier = modifier)
}

/**
 * E2 标题/正文的剩余字数（§4.8 指定 `XhsCharCounter`；超限自动转 `XhsColor.Error`）。
 *
 * 与线框的「剩余 187 / 200」是同一信息的两种呈现（组件固定为「已用/上限」）。
 */
@Composable
internal fun PublishCounter(current: Int, max: Int, modifier: Modifier = Modifier) {
    XhsCharCounter(current = current, max = max, modifier = modifier.fillMaxWidth())
}

/**
 * E3 右上「下一步」64×32（Dimens.writeTextNextWidth/Height）：
 * 有输入才点亮；生成中禁用并显示「生成中...」。
 */
@Composable
internal fun WriteTextNextButton(
    enabled: Boolean,
    generating: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = enabled && !generating
    Box(
        modifier = modifier
            .width(Dimens.writeTextNextWidth)
            .height(Dimens.writeTextNextHeight)
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .background(if (active) XhsColor.Red else XhsColor.BtnGray)
            .clickable(enabled = active, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (generating) "生成中..." else "下一步",
            style = XhsType.buttonLabelSmall,
            color = if (active) Color.White else XhsColor.Text2,
            maxLines = 1,
        )
    }
}

/** 写文字画布的占位文案（线框 E3 原文）。 */
internal const val WriteCanvasTitle = "写想法"
internal const val WriteCanvasHint = "说点什么或提个问题..."

/**
 * E3 底部「写长文」卡（高 98：icon 46→48 档、副文案 11sp、箭头 24）。
 *
 * 线框只把它作为**长文写作的视觉入口**，除聚焦画布外无独立行为；线框未给副文案定稿，
 * 此处为自拟占位文案（原型注：E3/E2 的表单细节待产品确认）。
 */
@Composable
internal fun WriteLongFormCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.writeTextCardHeight)
            .clip(RoundedCornerShape(Dimens.radiusCard))
            .background(XhsColor.BgGray)
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.s16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.entryIconBox)
                .clip(RoundedCornerShape(Dimens.entryIconRadius))
                .background(XhsColor.Bg),
            contentAlignment = Alignment.Center,
        ) {
            // 项目内无「长文」专用素材：按 §4.5 统一用占位素材（验收时一眼可见待替换）
            Icon(
                painter = painterResource(PlaceholderIconRes),
                contentDescription = null,
                tint = XhsColor.Text3,
                modifier = Modifier.size(Dimens.icon24),
            )
        }

        Spacer(modifier = Modifier.width(Dimens.s12))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = "写长文", style = XhsType.sectionTitle, color = XhsColor.Text1)
            Text(
                text = "记录更完整的想法",
                style = XhsType.captionSub,
                color = XhsColor.Text2,
            )
        }

        XhsChevronRight(size = Dimens.icon24)
    }
}
