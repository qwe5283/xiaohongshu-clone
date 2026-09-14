package com.xiaohongshu.app.data.api

import com.xiaohongshu.app.core.net.ApiEnvelope
import com.xiaohongshu.app.data.dto.UploadResultDto
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * 文件上传模块（契约 §8）。
 *
 * E6 提交流程：先逐个上传媒体拿 URL，再 `POST /api/post/create`。
 * E3 的文字配图由 `POST /api/post/text-image` 直接返回 URL，不走这里。
 */
interface UploadApi {

    /** 图片 ≤10MB。 */
    @Multipart
    @POST("api/upload/image")
    suspend fun uploadImage(@Part file: MultipartBody.Part): ApiEnvelope<UploadResultDto>

    /** 视频 ≤200MB。 */
    @Multipart
    @POST("api/upload/video")
    suspend fun uploadVideo(@Part file: MultipartBody.Part): ApiEnvelope<UploadResultDto>
}
