package com.xiaohongshu.app.core.publish

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 待发布的媒体项。[isVideo] 用于区分图/视频。 */
data class DraftMedia(
    /** 本地文件绝对路径（相册/相机/文字配图落盘；视频封面为首帧截取落盘的图片）。 */
    val localPath: String,
    val isVideo: Boolean,
    val mimeType: String,
    /** 预览图：图片用自身，视频用封面占位。 */
    val previewPath: String = localPath,
    /** 上传成功后的远端 URL；为空表示尚未上传。 */
    val remoteUrl: String = "",
    val uploading: Boolean = false,
    val failed: Boolean = false,
)

/**
 * 发布草稿（E2/E3 之间的交接载体），媒体结构**两态互斥**（契约 §2 变更 #20）：
 *
 * - **图文态**（`isVideoMode == false`）：`media` 为 1–9 张图片，无视频；
 * - **视频态**（`isVideoMode == true`）：`media` 恰好两项 = `[视频, 封面图]`，
 *   封面默认取视频首帧（客户端截取），用户可重新选择。
 *
 * 为什么用共享状态而不是导航参数：媒体列表可能含 9 个本地文件路径 + 上传状态，
 * 塞进 route 参数既丑陋又易丢；E3「写文字」生成配图后也要直接追加到 E2 的媒体列表。
 *
 * 生命周期：E1 弹层选完媒体 → [reset] 并填入 → E2 读取与编辑 → 发布成功或放弃时 [reset]。
 */
class PublishDraft {

    val media = mutableStateListOf<DraftMedia>()

    /** E3 → E2 的文字预填（作为标题）。 */
    var prefilledTitle by mutableStateOf("")

    /** E3 生成的配图 URL（已上传），进入 E2 时并入 [media]。 */
    var generatedImageUrl by mutableStateOf("")

    /** 是否处于提交中（E5-1）：禁用「发布」与返回。 */
    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    /** 提交失败的错误文案（Toast 呈现）。 */
    private val _error = MutableStateFlow("")
    val error: StateFlow<String> = _error.asStateFlow()

    fun setSubmitting(value: Boolean) {
        _submitting.value = value
    }

    fun setError(message: String) {
        _error.value = message
    }

    // ------------------------------------------------------------ 两态判定

    /** 视频态判定：草稿里存在视频（两态互斥，视频存在即视频态）。 */
    val isVideoMode: Boolean get() = media.any { it.isVideo }

    /** 视频态的视频项（图文态为 null）。 */
    val video: DraftMedia? get() = media.firstOrNull { it.isVideo }

    /** 视频态的封面项（图文态为 null）。 */
    val cover: DraftMedia? get() = media.firstOrNull { !it.isVideo }

    // ------------------------------------------------------------ 媒体编辑

    /**
     * 图文态追加**图片**（累计 ≤9）；含视频或超限时拒绝并返回 false。
     * 视频态的进入/替换走 [enterVideoMode] / [replaceVideo]。
     */
    fun addMedia(items: List<DraftMedia>): Boolean {
        if (items.any { it.isVideo }) return false
        if (isVideoMode) return false
        val images = imageCount + items.size
        if (images > MAX_IMAGE) return false
        media.addAll(items)
        return true
    }

    fun removeAt(index: Int) {
        if (index in media.indices) media.removeAt(index)
    }

    fun updateMedia(index: Int, transform: (DraftMedia) -> DraftMedia) {
        if (index in media.indices) media[index] = transform(media[index])
    }

    /** 是否满足「至少一图或一视频」（防御性校验项：两态下草稿非空即满足）。 */
    val hasMedia: Boolean get() = media.isNotEmpty()

    val imageCount: Int get() = media.count { !it.isVideo }

    val videoCount: Int get() = media.count { it.isVideo }

    /** 上传仍进行中或存在失败项时不允许提交（E5-1）。 */
    val hasPendingUpload: Boolean get() = media.any { it.uploading || it.failed || it.remoteUrl.isBlank() }

    /** 图文态：图片的远端 URL 列表（视频态下即封面一张）。 */
    fun remoteImageUrls(): List<String> = media.filterNot { it.isVideo }.map { it.remoteUrl }
        .filter { it.isNotBlank() }

    fun remoteVideoUrl(): String = media.firstOrNull { it.isVideo }?.remoteUrl.orEmpty()

    // ------------------------------------------------------------ 视频态切换

    /** 进入视频态（E1 混选 / E2 图文态确认切换）：清空已有图片，置 [video] + [cover]。 */
    fun enterVideoMode(video: DraftMedia, cover: DraftMedia) {
        media.clear()
        media.add(video)
        media.add(cover)
    }

    /** 视频态换视频：**同时**用新视频首帧重新生成封面（契约 §2 变更 #20）。 */
    fun replaceVideo(video: DraftMedia, cover: DraftMedia) {
        if (!isVideoMode) return
        media[VIDEO_INDEX] = video
        media[COVER_INDEX] = cover
    }

    /** 视频态换封面（保留视频项的上传状态）。 */
    fun replaceCover(cover: DraftMedia) {
        if (!isVideoMode) return
        media[COVER_INDEX] = cover
    }

    /** 视频态退回图文态：移除视频，封面图保留为唯一图片。 */
    fun exitVideoMode() {
        if (!isVideoMode) return
        val keepCover = cover
        media.clear()
        if (keepCover != null) media.add(keepCover)
    }

    fun reset() {
        media.clear()
        prefilledTitle = ""
        generatedImageUrl = ""
        _submitting.value = false
        _error.value = ""
    }

    companion object {
        /** 约束：图文态图片 ≤9；视频恰好 1 个（契约 §2 变更 #20）。 */
        const val MAX_IMAGE = 9
        const val MAX_VIDEO = 1

        /** 视频态媒体列表固定下标：[视频, 封面]。 */
        const val VIDEO_INDEX = 0
        const val COVER_INDEX = 1

        /** 标题 ≤200、正文 ≤10000。 */
        const val MAX_TITLE = 200
        const val MAX_CONTENT = 10_000

        /** E3 输入 → 配图接口的文本上限。 */
        const val TEXT_IMAGE_MAX_INPUT = 100
    }
}
