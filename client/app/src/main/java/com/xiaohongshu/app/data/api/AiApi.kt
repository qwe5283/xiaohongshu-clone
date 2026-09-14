package com.xiaohongshu.app.data.api

import com.xiaohongshu.app.core.net.ApiEnvelope
import com.xiaohongshu.app.data.dto.ChatRequest
import com.xiaohongshu.app.data.dto.ChatResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * AI 助手「点点」模块（契约 §10）。使用 70s 长超时客户端（[com.xiaohongshu.app.core.net.ApiClient]）。
 */
interface AiApi {

    /**
     * 单轮问答。每次只发当前问题，不携带历史上下文；历史仅客户端内存保存（H2）。
     */
    @POST("api/ai/chat")
    suspend fun chat(@Body body: ChatRequest): ApiEnvelope<ChatResponseDto>

    /** H1 空态建议问题 chips（契约变更 #11）。失败时客户端使用本地兜底文案。 */
    @GET("api/ai/suggestions")
    suspend fun suggestions(): ApiEnvelope<List<String>>
}
