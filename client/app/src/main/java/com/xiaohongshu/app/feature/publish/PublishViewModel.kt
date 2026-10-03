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

// ---- 文案（定稿文案照抄；自拟的注明来源）----

/** E4 定稿原文（Toast 呈现）。 */
internal const val MsgTitleRequired = "请输入笔记标题"

/** E4 定稿原文（Toast 呈现）。 */
internal const val MsgTitleTooLong = "标题不能超过200个字符"

/** §7 E4 / 契约 §9 `2005`（Toast 呈现）。 */
internal const val MsgNeedMedia = "笔记必须包含至少一张图片或一个视频"

/** E4 / 契约 §9 `2004`（Toast）。 */
internal const val MsgImageLimit = "最多上传9张图片"

/** 契约只有图片超限文案，视频上限提示为同构自拟（§7 E2）。 */
internal const val MsgVideoLimit = "最多上传1个视频"

/** E5-2 定稿原文。 */
internal const val MsgPublishSuccess = "发布成功！"

/** §7 E3 原文。 */
internal const val MsgGenerateFailed = "生成配图失败，请稍后重试"

/** 自拟：图文态唯一图片不可删（两态下草稿不允许为空，无素材笔记无法发布）。 */
internal const val MsgKeepLastImage = "至少保留一张图片"

/** 自拟：E2 图文态选到视频时的二次确认问句（XhsConfirmSheet 必须为问句）。 */
internal const val MsgVideoSwitchConfirm = "切换为视频笔记将清除已选图片，确认切换吗？"

/** 自拟：视频首帧截取失败（不切换状态，留在原图文态）。 */
internal const val MsgCoverGenerateFailed = "视频封面生成失败，请重试"

/** 契约 §9 `2006`（客户端恒带封面，仅防御）。 */
internal const val MsgVideoCoverRequired = "视频笔记必须包含封面图"

/**
 * E2 / E5-1 / E5-2 / E7 的发布表单 ViewModel。
 *
 * 状态归属：媒体列表与提交态在 [PublishDraft]（跨 E1/E3 的共享草稿，规范 §1）；
 * 标题/正文是表单局部状态，留在本 VM（`prefilledTitle` 仅在进入本页时取一次初值）。
 *
 * 校验失败一律 **Toast**（E4 错误条已废弃）：`draft.error` 经 Route 观察 → Toast → 清空。
 */
class PublishViewModel(
    private val draft: PublishDraft,
    private val uploads: UploadRepository,
    private val posts: PostRepository,
    private val toasts: ToastController,
) : ViewModel() {

    /** E3 预填标题（**不截断**：超 200 交由校验拦截，见 §7 E3）。 */
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

    /** 图文态选到视频后的切换确认弹层（②）。 */
    var showVideoSwitchConfirm by mutableStateOf(false)
        private set

    /** 切换确认暂存的视频项（确认后截首帧进视频态）。 */
    var pendingVideo by mutableStateOf<DraftMedia?>(null)
        private set

    /** 媒体以草稿为唯一真源；读取即在组合期订阅 `SnapshotStateList`。 */
    val media: List<DraftMedia> get() = draft.media

    /** 视频态判定与双槽内容（③）。 */
    val isVideoMode: Boolean get() = draft.isVideoMode
    val video: DraftMedia? get() = draft.video
    val cover: DraftMedia? get() = draft.cover

    /** E5-1 提交中（禁用「发布」、← 不可返回）。 */
    val submitting: StateFlow<Boolean> = draft.submitting

    /** 校验/提交失败文案（Route 观察 → Toast → [clearError]）。 */
    val error: StateFlow<String> = draft.error

    // ------------------------------------------------------------------ 表单编辑

    fun onTitleChange(value: String) {
        title = value
        // 用户已开始修正 → 清掉上一次的校验错误（「修正后可再次提交」）
        if (draft.error.value.isNotEmpty()) draft.setError("")
    }

    /** 正文 ≤10000：超出直接截断（无正文超限提示）。 */
    fun onContentChange(value: String) {
        content = value.take(PublishDraft.MAX_CONTENT)
    }

    fun clearError() {
        draft.setError("")
    }

    // ------------------------------------------------------------------ 媒体编辑（图文态）

    /**
     * 并入新选中的媒体（E2「＋」/拍摄）。
     *
     * 两态互斥：图文态只收图片（累计 ≤9，超出丢弃并 Toast）；**选到视频 → 暂存并弹
     * 二次确认**（确认才清除图片切换视频态，见 [confirmSwitchToVideo]）。视频态的
     * 换视频/换封面走 [replaceVideo] / [replaceCover]（双槽单选，不经本方法）。
     */
    fun onMediaAcquired(items: List<DraftMedia>) {
        if (draft.submitting.value) return
        if (draft.isVideoMode) return

        val videos = items.filter { it.isVideo }
        if (videos.isNotEmpty()) {
            if (videos.size > PublishDraft.MAX_VIDEO) toasts.show(MsgVideoLimit)
            pendingVideo = videos.first()
            showVideoSwitchConfirm = true
            return
        }

        val result = draft.acceptImages(items)
        if (result.imageOverflow) toasts.show(MsgImageLimit)
        if (result.accepted > 0 && draft.error.value.isNotEmpty()) draft.setError("")
    }

    /**
     * 图文态删除图片：**唯一仅剩的一张不响应**（Toast 反馈）——
     * 草稿为空 = 无素材笔记，无法发布也不允许回到空态。
     */
    fun removeMedia(index: Int) {
        // E5-1：提交中不允许增删媒体（会打乱正在上传的下标）
        if (draft.submitting.value) return
        if (draft.isVideoMode) return
        if (draft.media.size <= 1) {
            toasts.show(MsgKeepLastImage)
            return
        }
        draft.removeAt(index)
    }

    fun openAddMediaSheet() {
        if (draft.submitting.value) return
        // 「最多上传9张图片」判断提前到弹层之前：图满 9 → Toast，不弹 XhsActionSheet
        if (!draft.isVideoMode && draft.imageCount >= PublishDraft.MAX_IMAGE) {
            toasts.show(MsgImageLimit)
            return
        }
        showAddMediaSheet = true
    }

    fun dismissAddMediaSheet() {
        showAddMediaSheet = false
    }

    // ------------------------------------------------------------------ 视频态切换（②③）

    /** 二次确认「取消」：什么都不加，留在图文态。 */
    fun cancelSwitchToVideo() {
        showVideoSwitchConfirm = false
        pendingVideo = null
    }

    /** 二次确认「确认」：首帧已截好 → 清除图片、进视频态（首帧截取由 Route 完成）。 */
    fun confirmSwitchToVideo(video: DraftMedia, cover: DraftMedia) {
        if (draft.submitting.value) return
        draft.enterVideoMode(video, cover)
        showVideoSwitchConfirm = false
        pendingVideo = null
        if (draft.error.value.isNotEmpty()) draft.setError("")
    }

    /** 视频槽换视频：同时重截首帧生成新封面（③，封面 DraftMedia 由 Route 截好传入）。 */
    fun replaceVideo(video: DraftMedia, cover: DraftMedia) {
        if (draft.submitting.value) return
        draft.replaceVideo(video, cover)
    }

    /** 封面槽换封面（仅图片，视频不动）。 */
    fun replaceCover(cover: DraftMedia) {
        if (draft.submitting.value) return
        draft.replaceCover(cover)
    }

    /** 视频槽右上 ×：退回图文态，封面图保留为唯一图片（③）。 */
    fun exitVideoMode() {
        if (draft.submitting.value) return
        draft.exitVideoMode()
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
     * 「发布」：先校验（失败 → Toast），再走 E6 顺序（逐个上传媒体 → 创建笔记）。
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

        // E6 ③创建笔记（视频态 imageUrls 恰好为封面一张，契约 §2 变更 #20；超时 15s 已在 ApiClient 全局配置）
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
                // 契约 §9 `2004`/`2006` 客户端行为是 Toast，其余失败同样以 Toast 透传
                if (created is ApiResult.Biz && created.code == Code.IMAGE_LIMIT) {
                    toasts.show(MsgImageLimit)
                    draft.setError("")
                } else {
                    draft.setError(publishErrorMessage(created))
                }
            }
        }
    }

    /** 上传/创建失败的文案：`2005`/`2006` 用定稿文案，其余透传（I2）。 */
    private fun publishErrorMessage(result: ApiResult<*>): String = when (result) {
        is ApiResult.Biz -> when (result.code) {
            Code.NEED_MEDIA -> MsgNeedMedia
            Code.IMAGE_LIMIT -> MsgImageLimit
            Code.VIDEO_COVER_REQUIRED -> MsgVideoCoverRequired
            else -> result.userMessage()
        }

        else -> result.userMessage()
    }
}
