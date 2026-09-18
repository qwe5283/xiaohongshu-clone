package com.xiaohongshu.app.feature.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.xiaohongshu.app.R
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.design.XhsType
import com.xiaohongshu.app.core.ui.PlaceholderIconRes
import com.xiaohongshu.app.core.ui.XhsConfirmSheet
import com.xiaohongshu.app.core.ui.XhsDivider
import com.xiaohongshu.app.core.util.Formatters
import com.xiaohongshu.app.di.LocalAppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * F1「我」的三个页签（线框 F1：笔记 / 收藏 / 赞过）。
 *
 * 「赞过」仅自己可见（线框 F1 注），故 [Public] 供 F2 他人主页复用前两个。
 */
internal enum class ProfileTab(
    val label: String,
    val iconRes: Int,
    /** 空态文案（§4.7 已定稿：暂无笔记 / 暂无收藏 / 暂无点赞）。 */
    val emptyText: String,
) {
    NOTES("笔记", R.drawable.ic_publish, "暂无笔记"),
    COLLECTED("收藏", R.drawable.ic_star, "暂无收藏"),
    LIKED("赞过", R.drawable.ic_heart, "暂无点赞");

    companion object {
        /** 线框 f2：他人主页 Tab 仅「笔记 / 收藏」（无「赞过」）。 */
        val Public: List<ProfileTab> = listOf(NOTES, COLLECTED)
    }
}

/** 统计数字：0 也显示（与列表计数的「0 → 文字标签」规则不同，线框 F1 统计行始终显示数字）。 */
internal fun statText(count: Long): String =
    if (count <= 0) "0" else Formatters.formatCount(count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())

/**
 * F1/F2 页签行（线框 F1：h 44、条目宽 64/86、icon 18；右侧 44×44 搜索框为**视觉占位**）。
 *
 * 搜索框无线框定义的行为与素材，故按 §4.5 统一用 [PlaceholderIconRes] 占位。
 */
@Composable
internal fun ProfileSegmentRow(
    tabs: List<ProfileTab>,
    selected: ProfileTab,
    onSelect: (ProfileTab) -> Unit,
    modifier: Modifier = Modifier,
    showSearchBox: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.segmentBar),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.width(Dimens.pagePadding))
            tabs.forEach { tab ->
                ProfileSegmentItem(
                    tab = tab,
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (showSearchBox) {
                Box(
                    modifier = Modifier.size(Dimens.segmentSearchBox),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(PlaceholderIconRes),
                        contentDescription = null,
                        tint = XhsColor.Text2,
                        modifier = Modifier.size(Dimens.icon20),
                    )
                }
                Spacer(modifier = Modifier.width(Dimens.s4))
            }
        }
        XhsDivider()
    }
}

@Composable
private fun ProfileSegmentItem(
    tab: ProfileTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .width(Dimens.segmentItemWide)
            .height(Dimens.segmentBar)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(tab.iconRes),
            contentDescription = null,
            tint = if (selected) XhsColor.Text1 else XhsColor.Text2,
            modifier = Modifier.size(Dimens.segmentIcon),
        )
        Spacer(modifier = Modifier.width(Dimens.s4))
        Text(
            text = tab.label,
            style = XhsType.s(15, emphasis = selected),
            color = if (selected) XhsColor.Text1 else XhsColor.Text2,
            maxLines = 1,
        )
    }
}

/** F1 小红书号旁的复制（真实实现：写入系统剪贴板）。 */
internal fun copyToClipboard(context: Context, label: String, text: String) {
    if (text.isBlank()) return
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText(label, text))
}

// ---------------------------------------------------------------- 本地选图（F3 头像 / 背景图）

/** 头像/背景图选择结果（走系统照片选择器，无需任何运行时权限）。 */
internal class ProfileImagePicker(
    /** 头像。 */
    val pickAvatar: () -> Unit,
    /** 背景图。 */
    val pickBackground: () -> Unit,
)

/**
 * F3 头像 / 背景图：`ActivityResultContracts.PickVisualMedia`（**单张、无需权限**），
 * 选中后立即拷贝到 `cache/profile/` 落盘，再交给调用方做**即时预览**；
 * 「保存」时该文件由 `UploadRepository.uploadImage` 上传换 URL（契约 §1.5 / §8）。
 *
 * @param onPicked 已落盘的本地路径
 * @param onFailure 落盘失败（自拟兜底文案，线框未定义该异常）
 */
@Composable
internal fun rememberProfileImagePicker(
    onPicked: (String) -> Unit,
    onFailure: (String) -> Unit,
): ProfileImagePicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun launch(launcher: androidx.activity.result.ActivityResultLauncher<PickVisualMediaRequest>) {
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    val avatarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) { copyToProfileCache(context, uri) }
            if (path == null) onFailure(ProfileImageReadFailed) else onPicked(path)
        }
    }

    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) { copyToProfileCache(context, uri) }
            if (path == null) onFailure(ProfileImageReadFailed) else onPicked(path)
        }
    }

    return ProfileImagePicker(
        pickAvatar = { launch(avatarLauncher) },
        pickBackground = { launch(backgroundLauncher) },
    )
}

/** 自拟兜底文案（线框未定义本地读图失败文案，与 WS-Publish 的同类兜底保持一致口径）。 */
internal const val ProfileImageReadFailed = "图片读取失败，请重试"

private const val PROFILE_CACHE_DIR = "profile"

/** 已选图片落盘目录：`cache/profile/`（上传需要真实 `File`，且与选择器 URI 生命周期解耦）。 */
private fun copyToProfileCache(context: Context, uri: Uri): String? = runCatching {
    val dir = File(context.cacheDir, PROFILE_CACHE_DIR).apply { mkdirs() }
    val target = File(dir, "picked_${UUID.randomUUID()}.jpg")
    val copied = context.contentResolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    } != null
    if (!copied || target.length() <= 0L) {
        target.delete()
        null
    } else {
        target.absolutePath
    }
}.getOrNull()

/**
 * 本地图片即时预览（F3 头像 / 背景图）。
 *
 * 走 `BitmapFactory` 而非核心 [com.xiaohongshu.app.core.ui.XhsAsyncImage]：后者基于 `ImageLoader`
 * （OkHttp 下载），无法读 `content://`/本地文件。按 [PREVIEW_SAMPLE_SIZE] 下采样，避免原图撑爆内存。
 */
@Composable
internal fun LocalImagePreview(
    path: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = path) {
        value = if (path.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    BitmapFactory
                        .decodeFile(path, previewOptions())
                        ?.asImageBitmap()
                }.getOrNull()
            }
        }
    }

    Box(
        modifier = modifier.background(XhsColor.PlaceholderBg),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = null,
                contentScale = contentScale,
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

/** 预览下采样倍数（仅为内存保护，非设计尺寸）。 */
private const val PREVIEW_SAMPLE_SIZE = 4

private fun previewOptions(): BitmapFactory.Options = BitmapFactory.Options().apply {
    inSampleSize = PREVIEW_SAMPLE_SIZE
}

// ---------------------------------------------------------------- F6 退出登录确认（F4 / F5 共用）

/**
 * F6 退出登录确认（线框 F6：遮罩 + 底部确认/取消，与 E7/G6 同款结构 → `XhsConfirmSheet`）。
 *
 * 确认 → 清登录态 + 清角标（并丢弃上个用户的互动覆盖）→ 回游客首页（A1）；
 * 取消 / 点遮罩 → 仅关闭弹层（由 [XhsConfirmSheet] 的 `onDismiss` 承担）。
 */
@Composable
internal fun ProfileLogoutSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()

    XhsConfirmSheet(
        visible = visible,
        confirmText = "退出登录",
        cancelText = "取消",
        onDismiss = onDismiss,
        onConfirm = {
            scope.launch {
                container.sessionManager.logout()
                container.interactionStore.clear()
                container.unreadCountCenter.clear()
                onLoggedOut()
            }
        },
        modifier = modifier,
    )
}
