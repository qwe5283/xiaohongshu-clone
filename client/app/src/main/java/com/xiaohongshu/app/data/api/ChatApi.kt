package com.xiaohongshu.app.data.api

import com.xiaohongshu.app.core.net.ApiEnvelope
import com.xiaohongshu.app.data.dto.ChatMessageDto
import com.xiaohongshu.app.data.dto.ChatReadResultDto
import com.xiaohongshu.app.data.dto.ConversationDto
import com.xiaohongshu.app.data.dto.CreateConversationRequest
import com.xiaohongshu.app.data.dto.PageDto
import com.xiaohongshu.app.data.dto.SendMessageRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * IM 私信模块（契约 §12）。全部需登录。
 *
 * 实时下行（新消息/已读回执/通知未读数）不走本接口，走 `GET /ws/chat` 长连接
 * （见 [com.xiaohongshu.app.core.realtime.RealtimeConnection]）。
 */
interface ChatApi {

    /** §12.2 会话列表：我参与的会话，按 lastMessageTime 倒序，只返回有消息的。 */
    @GET("api/chat/conversations")
    suspend fun conversations(
        @Query("pageNum") pageNum: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
    ): ApiEnvelope<PageDto<ConversationDto>>

    /**
     * §12.3 历史消息：按 `id` 升序（时间正序），[pageNum] 越大越旧。
     * 非会话成员 → 8001。
     */
    @GET("api/chat/{conversationId}/messages")
    suspend fun messages(
        @Path("conversationId") conversationId: Long,
        @Query("pageNum") pageNum: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
    ): ApiEnvelope<PageDto<ChatMessageDto>>

    /** §12.4 创建/获取会话（同一对用户幂等复用）。 */
    @POST("api/chat/conversations")
    suspend fun createConversation(
        @Body body: CreateConversationRequest,
    ): ApiEnvelope<ConversationDto>

    /**
     * §12.5 发送消息。[body] 带 clientId（服务端幂等去重）。
     * 响应即服务端落库后的消息（含 id/createTime），用于替换本地临时消息。
     */
    @POST("api/chat/{conversationId}/messages")
    suspend fun sendMessage(
        @Path("conversationId") conversationId: Long,
        @Body body: SendMessageRequest,
    ): ApiEnvelope<ChatMessageDto>

    /** §12.6 已读上报：把该会话里对方发来且我未读的全部标为已读。 */
    @PUT("api/chat/{conversationId}/read")
    suspend fun markRead(
        @Path("conversationId") conversationId: Long,
    ): ApiEnvelope<ChatReadResultDto>
}
