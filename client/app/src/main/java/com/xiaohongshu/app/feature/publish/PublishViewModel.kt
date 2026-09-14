package com.xiaohongshu.app.feature.publish

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohongshu.app.core.net.ApiResult
import com.xiaohongshu.app.core.net.Code
import com.xiaohongshu.app.core.net.userMessage
import com.xiaohongshu.app.core.publish.DraftMedia
import com.xiaohongshu.app.core.publish.PublishDraft
import com.xiaohongshu.app.core.ui.ToastController
import com.xiaohongshu.app.data.repo.PostRepository
import com.xiaohongshu.app.data.repo.UploadRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

// ---- 文案（线框已定稿的照抄；自拟的注明来源）----

/** 线框 E4 原文。 */
internal const val MsgTitleRequired = "请输入笔记标题"

/** 线框 E4 原文。 */
internal const val MsgTitleTooLong = "标题不能超过200个字符"

/** §7 E4 / 契约 §9 `2005`（E4 错误条，非 Toast）。 */
internal const val MsgNeedMedia = "笔记必须包含至少一张图片或一个视频"

/** 线框 E4 / 契约 §9 `2004`（Toast）。 */
internal const val MsgImageLimit = "最多上传9张图片"

/** 契约只有图片超限文案，视频上限提示为同构自拟（§7 E2「视频 ≤1」）。 */
internal const val MsgVideoLimit = "最多上传1个视频"

/** 线框 E5-2 原文。 */
internal const val MsgPublishSuccess = "发布成功！"

/** §7 E3 原文。 */
internal const val MsgGenerateFailed = "生成配图失败，请稍后重试"

/**
 * E2 / E4 / E5-1 / E5-2 / E7 的发布表单 ViewModel。
 *
 * 状态归属：媒体列表与提交态在 [PublishDraft]（跨 E1/E3 的共享草稿，规范 §1）；
 * 标题/正文是表单局部状态，留在本 VM（`prefilledTitle` 仅在进入本页时取一次初值）。
 */
class PublishViewModel(
    private val draft: PublishDraft,
    private val uploads: UploadRepository,
    private val posts: PostRepository,
    private val toasts: ToastController,
) : ViewModel() {

    /** E3 预填标题（**不截断**：超 200 交由 E4 校验拦截，见 §7 E3）。 */
    var title by mutableStateOf(draft.prefilledTitle)
        private set

    var content by mutableStateOf("")
        private set

    /** E7 放弃确认弹层。 */
    var showDiscardConfirm by mutableStateOf(false)
        private set

    /** E2 的「＋」添加媒体弹层（从相册选择 / 拍摄，无「写文字」入口）。 */
    var showAddMediaSheet by mutableStateOf(false)
        private set

    /** 媒体以草稿为唯一真源；读取即在组合期订阅 `SnapshotStateList`。 */
    val media: List<DraftMedia> get() = draft.media

    /** E5-1 提交中（禁用「发布」、← 不可返回）。 */
    val submitting: StateFlow<Boolean> = draft.submitting

    /** E4 顶部错误条文案。 */
    val error: StateFlow<String> = draft.error

    // ------------------------------------------------------------------ 表单编辑

    fun onTitleChange(value: String) {
        title = value
        // 用户已开始修正 → 清掉上一次的校验错误条（E4「修正后可再次提交」）
        if (draft.error.value.isNotEmpty()) draft.setError("")
    }

    /** 正文 ≤10000：超出直接截断（线框 E4 未定义正文超限错误条）。 */
    fun onContentChange(value: String) {
        content = value.take(PublishDraft.MAX_CONTENT)
    }

    /**
     * 并入新选中的媒体（E2「＋」）。
     *
     * 上限为**累计**校验（图 ≤9、视频 ≤1，可共存）：超出部分丢弃并 Toast
     * （§7 E2/E4「图片超 9 → 最多上传9张图片」）。
     */
    fun onMediaAcquired(items: List<DraftMedia>) {
        val result = draft.acceptMedia(items)
        if (result.imageOverflow) toasts.show(MsgImageLimit)
        if (result.videoOverflow) toasts.show(MsgVideoLimit)
        if (result.accepted > 0 && draft.error.value.isNotEmpty()) draft.setError("")
    }

    fun removeMedia(index: Int) {
        // E5-1：提交中不允许增删媒体（会打乱正在上传的下标）
        if (draft.submitting.value) return
        draft.removeAt(index)
    }

    fun openAddMediaSheet() {
        showAddMediaSheet = true
    }

    fun dismissAddMediaSheet() {
        showAddMediaSheet = false
    }

    // ------------------------------------------------------------------ E2 ← / E7

    fun onBackPressed() {
        if (draft.submitting.value) return // E5-1：提交中 ← 不可返回
        showDiscardConfirm = true
    }

    fun dismissDiscardConfirm() {
        showDiscardConfirm = false
    }

    /** E7 确认放弃：丢弃草稿（`reset` 由 Route 在调用后配合 `navigator.toMain()`）。 */
    fun discardDraft() {
        showDiscardConfirm = false
        draft.reset()
    }

    // ------------------------------------------------------------------ E5 提交

    /**
     * 「发布」：先校验（失败 → E4），再走 E6 顺序（逐个上传媒体 → 创建笔记）。
     *
     * [onSuccess] 由 Route 传入，用于 E5-2 的 `navigator.toMain()`（一次性事件走回调，规范 §3）。
     */
    fun submit(onSuccess: () -> Unit) {
        if (draft.submitting.value) return
        val normalizedTitle = title.trim()
        validate(normalizedTitle)?.let { message ->
            draft.setError(message)
            return
        }
        viewModelScope.launch { runSubmit(normalizedTitle, onSuccess) }
    }

    /** 客户端校验（标题必填/长度、至少一图或一视频）；返回 null 表示通过。 */
    private fun validate(normalizedTitle: String): String? = when {
        normalizedTitle.isEmpty() -> MsgTitleRequired
        normalizedTitle.length > PublishDraft.MAX_TITLE -> MsgTitleTooLong
        draft.media.isEmpty() -> MsgNeedMedia
        else -> null
    }

    private suspend fun runSubmit(normalizedTitle: String, onSuccess: () -> Unit) {
        draft.setError("")
        draft.setSubmitting(true)

        // E6 ①逐张图片 ②视频：已拿到 URL 的跳过（失败重试不重复上传）
        draft.media.forEachIndexed { index, item ->
            if (item.remoteUrl.isNotBlank()) return@forEachIndexed
            draft.updateMedia(index) { it.copy(uploading = true, failed = false) }

            val file = File(item.localPath)
            val result = if (item.isVideo) {
                uploads.uploadVideo(file, item.mimeType)
            } else {
                uploads.uploadImage(file, item.mimeType)
            }

            when (result) {
                is ApiResult.Ok -> draft.updateMedia(index) {
                    it.copy(remoteUrl = result.data, uploading = false, failed = false)
                }

                else -> {
                    draft.updateMedia(index) { it.copy(uploading = false, failed = true) }
                    draft.setError(publishErrorMessage(result))
                    draft.setSubmitting(false)
                    return
                }
            }
        }

        // E6 ③创建笔记（视频封面由后端生成；超时 15s 已在 ApiClient 全局配置）
        val created = posts.create(
            title = normalizedTitle,
            content = content.trim(),
            imageUrls = draft.remoteImageUrls(),
            videoUrl = draft.remoteVideoUrl(),
        )

        when (created) {
            is ApiResult.Ok -> {
                // E5-2：清草稿 → Toast → 回首页 Tab（列表由 WS-Home 的 ON_RESUME 静默刷新置顶）
                draft.reset()
                toasts.show(MsgPublishSuccess)
                onSuccess()
            }

            else -> {
                draft.setSubmitting(false)
                // 契约 §9 `2004` 客户端行为是 Toast（不是错误条），其余失败给 E4 错误条
                if (created is ApiResult.Biz && created.code == Code.IMAGE_LIMIT) {
                    toasts.show(MsgImageLimit)
                    draft.setError("")
                } else {
                    draft.setError(publishErrorMessage(created))
                }
            }
        }
    }

    /** 上传/创建失败的文案：`2005` 用线框定稿的「至少一图或一视频」，其余透传（I2）。 */
    private fun publishErrorMessage(result: ApiResult<*>): String = when (result) {
        is ApiResult.Biz -> when (result.code) {
            Code.NEED_MEDIA -> MsgNeedMedia
            Code.IMAGE_LIMIT -> MsgImageLimit
            else -> result.userMessage()
        }

        else -> result.userMessage()
    }
}
