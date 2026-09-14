package com.xiaohongshu.app.core.publish

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 待发布的媒体项。[isVideo] 用于区分图/视频（视频 ≤1）。 */
data class DraftMedia(
    /** 本地文件绝对路径（相册/相机/文字配图落盘）。 */
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
 * 发布草稿（E2/E3 之间的交接载体）。
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

    /** 提交失败的错误条文案（E4）。 */
    private val _error = MutableStateFlow("")
    val error: StateFlow<String> = _error.asStateFlow()

    fun setSubmitting(value: Boolean) {
        _submitting.value = value
    }

    fun setError(message: String) {
        _error.value = message
    }

    // ------------------------------------------------------------ 媒体编辑

    /** 图片上限 9、视频上限 1（同时校验两者不能共存超限）。 */
    fun addMedia(items: List<DraftMedia>): Boolean {
        val videos = media.count { it.isVideo } + items.count { it.isVideo }
        val images = media.count { !it.isVideo } + items.count { !it.isVideo }
        if (videos > MAX_VIDEO) return false
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

    /** 是否满足「至少一图或一视频」（E4 校验项）。 */
    val hasMedia: Boolean get() = media.isNotEmpty()

    val imageCount: Int get() = media.count { !it.isVideo }

    val videoCount: Int get() = media.count { it.isVideo }

    /** 上传仍进行中或存在失败项时不允许提交（E5-1）。 */
    val hasPendingUpload: Boolean get() = media.any { it.uploading || it.failed || it.remoteUrl.isBlank() }

    /** 媒体列表的远端 URL，按图片在前、视频在后的顺序供 create 使用。 */
    fun remoteImageUrls(): List<String> = media.filterNot { it.isVideo }.map { it.remoteUrl }
        .filter { it.isNotBlank() }

    fun remoteVideoUrl(): String = media.firstOrNull { it.isVideo }?.remoteUrl.orEmpty()

    fun reset() {
        media.clear()
        prefilledTitle = ""
        generatedImageUrl = ""
        _submitting.value = false
        _error.value = ""
    }

    companion object {
        /** 线框约束：图片 ≤9 张，视频 ≤1 个。 */
        const val MAX_IMAGE = 9
        const val MAX_VIDEO = 1

        /** 标题 ≤200、正文 ≤10000。 */
        const val MAX_TITLE = 200
        const val MAX_CONTENT = 10_000

        /** E3 输入 → 配图接口的文本上限。 */
        const val TEXT_IMAGE_MAX_INPUT = 100
    }
}
