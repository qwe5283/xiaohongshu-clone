package com.xiaohongshu.app.data.repo

import com.xiaohongshu.app.core.net.ApiResult
import com.xiaohongshu.app.core.net.apiCall
import com.xiaohongshu.app.core.net.map
import com.xiaohongshu.app.core.net.unwrap
import com.xiaohongshu.app.data.api.AiApi
import com.xiaohongshu.app.data.api.NotificationApi
import com.xiaohongshu.app.data.api.UploadApi
import com.xiaohongshu.app.data.dto.ChatRequest
import com.xiaohongshu.app.data.dto.NotificationCategory
import com.xiaohongshu.app.data.mapper.*
import com.xiaohongshu.app.domain.model.ChatMessage
import com.xiaohongshu.app.domain.model.NotificationItem
import com.xiaohongshu.app.domain.model.Paged
import com.xiaohongshu.app.domain.model.UnreadCounts
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/** 消息通知数据源（G1–G6）。 */
class NotificationRepository(private val notificationApi: NotificationApi) {

    /** 15s 轮询的未读数（总数 + 三分类）。 */
    suspend fun unreadCounts(): ApiResult<UnreadCounts> = apiCall {
        notificationApi.unreadCount().unwrap()
    }.map { it.toDomain() }

    /** G2/G3/G4 列表。[category] 对应三个入口；[type] 为更细的筛选。 */
    suspend fun list(
        category: NotificationCategory,
        page: Int,
        pageSize: Int,
    ): ApiResult<Paged<NotificationItem>> = apiCall {
        notificationApi.list(
            pageNum = page,
            pageSize = pageSize,
            category = category.value,
            type = null,
        ).unwrap()
    }.map { it.toNotificationPage() }

    /** 点条目时先标已读（角标 -1）再跳转；失败不阻断，下次轮询校正。 */
    suspend fun markRead(id: Long): ApiResult<Unit> = apiCall { notificationApi.markRead(id).unwrap() }

    /** G6 一键已读（作用于当前分类列表）。 */
    suspend fun markAllRead(category: NotificationCategory? = null): ApiResult<Unit> = apiCall {
        notificationApi.markAllRead(category?.value).unwrap()
    }
}

/** AI 助手「点点」数据源（H1–H3）。 */
class AiRepository(private val aiApi: AiApi) {

    /**
     * 单轮问答：[question] 只发当前问题，不带历史上下文。
     * 返回 [ChatMessage]（含可选笔记卡），便于 ViewModel 直接追加到对话流。
     */
    suspend fun chat(question: String): ApiResult<ChatMessage> = apiCall {
        aiApi.chat(ChatRequest(message = question)).unwrap()
    }.map { dto ->
        ChatMessage(
            id = System.nanoTime(),
            fromUser = false,
            text = dto.answer,
            notes = dto.notes.map { it.toDomain() },
            state = ChatMessage.ChatState.Done,
        )
    }

    /** H1 空态建议问题；失败时由调用方使用本地兜底文案。 */
    suspend fun suggestions(): ApiResult<List<String>> = apiCall { aiApi.suggestions().unwrap() }

    companion object {
        /** 接口不可用时的本地兜底建议问题，保证 H1 不空窗。 */
        val FALLBACK_SUGGESTIONS = listOf(
            "帮我写一段周末徒步的文案",
            "推荐3个适合新手的妆容",
            "秋天穿搭怎么搭出高级感",
        )
    }
}

/**
 * 上传数据源（E6 提交流程第一步）。
 *
 * 只做「本地文件 → 远端 URL」的转换，不管并发与进度编排（那是发布 ViewModel 的职责）。
 */
class UploadRepository(private val uploadApi: UploadApi) {

    /** 图片 ≤10MB。 */
    suspend fun uploadImage(file: File, mimeType: String = "image/*"): ApiResult<String> =
        uploadMultipart(file, mimeType, isVideo = false)

    /** 视频 ≤200MB。 */
    suspend fun uploadVideo(file: File, mimeType: String = "video/*"): ApiResult<String> =
        uploadMultipart(file, mimeType, isVideo = true)

    private suspend fun uploadMultipart(
        file: File,
        mimeType: String,
        isVideo: Boolean,
    ): ApiResult<String> {
        val part = MultipartBody.Part.createFormData(
            name = "file",
            filename = file.name,
            body = file.asRequestBody(mimeType.toMediaType()),
        )
        return apiCall {
            if (isVideo) uploadApi.uploadVideo(part).unwrap() else uploadApi.uploadImage(part).unwrap()
        }.map { it.url }
    }

    /** 相机拍摄结果落盘后的上传（E1「拍摄」→ 系统相机 → E2）。 */
    suspend fun uploadBytes(bytes: ByteArray, filename: String, mimeType: String): ApiResult<String> {
        val part = MultipartBody.Part.createFormData(
            name = "file",
            filename = filename,
            body = bytes.toRequestBody(mimeType.toMediaType()),
        )
        return apiCall { uploadApi.uploadImage(part).unwrap() }.map { it.url }
    }
}
