package com.xiaohongshu.app.feature.publish

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.xiaohongshu.app.core.publish.DraftMedia
import com.xiaohongshu.app.core.publish.PublishDraft
import com.xiaohongshu.app.di.LocalAppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * 相机授权被拒的降级提示（§4.7「相机拒绝」文案已定稿，I4）。
 * 拒绝后**回到 E1**，用户可改走「从相册选择」（系统照片选择器无需权限）。
 */
internal const val CameraDeniedToast = "未授权相机，可从相册选择"

/** 本地媒体落盘失败（相册 URI 读取/相机落盘 IO 异常）：线框未定义，属自拟兜底文案。 */
internal const val MediaReadFailedToast = "媒体读取失败，请重试"

/**
 * E1 / E2 共用的媒体获取能力（线框 I4 + §7 E1「从相册选择 / 拍摄」）。
 *
 * 两条来源：
 * - [pickFromGallery] 系统照片选择器（`PickMultipleVisualMedia`，**多选、无需运行时权限**）；
 * - [takePhoto] 相机：先要 CAMERA 运行时权限（**全项目唯一运行时权限**），
 *   授权后经 `FileProvider` 写入 `cache/captures/` 再交给系统相机（`TakePicture`）。
 *
 * 选到的 `content://` URI 会**立即拷贝到 `cache/publish/`** 落盘为真实文件：
 * ① 照片选择器的读权限不跨进程重启，落盘后与 URI 生命周期解耦；
 * ② `UploadRepository`（§8）需要 `File` 才能做 multipart，且有独立的视频端点
 *   （`uploadBytes` 只能打图片端点，视频必须走 `uploadVideo(File)`）。
 */
internal class MediaAcquisition(
    /** 从相册多选。 */
    val pickFromGallery: () -> Unit,
    /** 拍摄（内部处理权限申请与落盘）。 */
    val takePhoto: () -> Unit,
)

/**
 * @param onAcquired 已落盘的媒体（相册多选为一批，拍摄为单个），调用方负责并入 [PublishDraft]
 * @param onCameraDenied 相机权限被拒（此时已弹 Toast，调用方只需决定「是否留在 E1」）
 * @param onFailure 媒体落盘失败
 */
@Composable
internal fun rememberMediaAcquisition(
    onAcquired: (List<DraftMedia>) -> Unit,
    onCameraDenied: () -> Unit,
    onFailure: (String) -> Unit,
): MediaAcquisition {
    val context = LocalContext.current
    val toasts = LocalAppContainer.current.toastController
    val scope = rememberCoroutineScope()

    /** 待写入的拍摄文件：`TakePicture` 只回传成功与否，落盘路径需自己记住。 */
    var pendingCapture by remember { mutableStateOf<File?>(null) }

    // ---- 拍摄：结果落盘 → 交给调用方 ----
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val file = pendingCapture
        pendingCapture = null
        if (success && file != null && file.length() > 0L) {
            onAcquired(
                listOf(
                    DraftMedia(
                        localPath = file.absolutePath,
                        isVideo = false,
                        mimeType = MIME_JPEG,
                    ),
                ),
            )
        }
    }

    fun launchCamera() {
        val file = runCatching { newCaptureFile(context) }.getOrNull()
        if (file == null) {
            onFailure(MediaReadFailedToast)
            return
        }
        pendingCapture = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraLauncher.launch(uri)
    }

    // ---- I4：相机权限（全项目唯一运行时权限，仅「拍摄」触发）----
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) launchCamera() else {
            toasts.show(CameraDeniedToast)
            onCameraDenied()
        }
    }

    // ---- 相册：系统照片选择器（多选，无需权限，可含视频）----
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(PublishDraft.MAX_IMAGE),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val imported = withContext(Dispatchers.IO) {
                uris.mapNotNull { copyToCacheDir(context, it) }
            }
            if (imported.isEmpty()) onFailure(MediaReadFailedToast) else onAcquired(imported)
        }
    }

    return MediaAcquisition(
        pickFromGallery = {
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
            )
        },
        takePhoto = {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED
            // 已授权直接进相机；否则走 I4 权限申请（首次触发系统弹窗）
            if (granted) launchCamera() else permissionLauncher.launch(Manifest.permission.CAMERA)
        },
    )
}

// ---------------------------------------------------------------- 落盘辅助

/** 一次媒体并入的结果（用于 §7 E2/E4 的「超出上限 → Toast」）。 */
internal data class MediaAcceptance(
    /** 实际并入草稿的条数。 */
    val accepted: Int,
    /** 图片超出 9 张上限。 */
    val imageOverflow: Boolean,
    /** 视频超出 1 个上限。 */
    val videoOverflow: Boolean,
)

/**
 * 把新选中的媒体并入草稿，**累计**校验「图 ≤9、视频 ≤1（可共存）」。
 *
 * 放在这里而不是各调用点：E1（相册多选可直接选到多个视频）与 E2「＋」都必须做同一套裁剪，
 * 否则 E1 会出现「选了 2 个视频后静默什么都不做」的空洞。
 */
internal fun PublishDraft.acceptMedia(items: List<DraftMedia>): MediaAcceptance {
    val images = items.filterNot { it.isVideo }
    val videos = items.filter { it.isVideo }
    val imageCapacity = (PublishDraft.MAX_IMAGE - imageCount).coerceAtLeast(0)
    val videoCapacity = (PublishDraft.MAX_VIDEO - videoCount).coerceAtLeast(0)

    val accepted = images.take(imageCapacity) + videos.take(videoCapacity)
    if (accepted.isNotEmpty()) addMedia(accepted)

    return MediaAcceptance(
        accepted = accepted.size,
        imageOverflow = images.size > imageCapacity,
        videoOverflow = videos.size > videoCapacity,
    )
}

private const val MIME_JPEG = "image/jpeg"
private const val CACHE_MEDIA_DIR = "publish"
private const val CACHE_CAPTURE_DIR = "captures"

/** 相册选中的 URI 拷贝到 `cache/publish/`（manifest 已开放 `cache/captures/` 与 `files/images/`）。 */
private fun copyToCacheDir(context: Context, uri: Uri): DraftMedia? = runCatching {
    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
    val target = File(
        mediaDir(context),
        "media_${UUID.randomUUID()}.${extensionOf(mimeType)}",
    )
    val copied = context.contentResolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    } != null
    if (!copied || target.length() <= 0L) {
        target.delete()
        null
    } else {
        DraftMedia(
            localPath = target.absolutePath,
            isVideo = mimeType.startsWith("video/"),
            mimeType = mimeType,
        )
    }
}.getOrNull()

/** 拍摄落盘文件（`cache/captures/`，与 `res/xml/file_paths.xml` 的 `<cache-path>` 对应）。 */
private fun newCaptureFile(context: Context): File {
    val dir = File(context.cacheDir, CACHE_CAPTURE_DIR).apply { mkdirs() }
    return File(dir, "IMG_${System.currentTimeMillis()}.jpg")
}

private fun mediaDir(context: Context): File =
    File(context.cacheDir, CACHE_MEDIA_DIR).apply { mkdirs() }

/** 仅用于文件名与 multipart 显示；真正的 MIME 传给 `UploadRepository`。 */
private fun extensionOf(mimeType: String): String = when {
    mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpg"
    mimeType.contains("png") -> "png"
    mimeType.contains("webp") -> "webp"
    mimeType.contains("gif") -> "gif"
    mimeType.contains("heic") || mimeType.contains("heif") -> "heic"
    mimeType.contains("quicktime") -> "mov"
    mimeType.startsWith("video/") -> "mp4"
    else -> "bin"
}
