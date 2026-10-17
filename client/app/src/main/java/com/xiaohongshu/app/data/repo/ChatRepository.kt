package com.xiaohongshu.app.data.repo

import com.xiaohongshu.app.core.net.ApiResult
import com.xiaohongshu.app.core.net.apiCall
import com.xiaohongshu.app.core.net.map
import com.xiaohongshu.app.core.net.unwrap
import com.xiaohongshu.app.data.api.ChatApi
import com.xiaohongshu.app.data.dto.CreateConversationRequest
import com.xiaohongshu.app.data.dto.SendMessageRequest
import com.xiaohongshu.app.data.mapper.toConversationPage
import com.xiaohongshu.app.data.mapper.toDomain
import com.xiaohongshu.app.data.mapper.toMessagePage
import com.xiaohongshu.app.domain.model.Conversation
import com.xiaohongshu.app.domain.model.ImMessage
import com.xiaohongshu.app.domain.model.Paged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * IM 私信数据源（契约 §12：G1 会话列表 + G8 私聊页）。
 *
 * 边界：本类只做「取数 + 映射」。实时推送（新消息/已读回执）由
 * [com.xiaohongshu.app.core.realtime.RealtimeConnection] 负责，页面层把两者接起来。
 */
class ChatRepository(private val chatApi: ChatApi) {

    /**
     * 读跟随写：会话列表与已读上报共用一把锁串行——G8 内对方新消息触发的已读上报
     * 在途时，返回 G1 的会话列表刷新等它落库再发。mockserver 的每请求随机延迟会让
     * 几乎同时发出的这组读写在服务端约半数乱序，把刚上报掉的未读红点又带回来
     * （乐观值回退闪跳）。
     */
    private val readYourWrites = Mutex()

    /** §12.2 会话列表（按 lastMessageTime 倒序；服务端只返回有消息的会话）。 */
    suspend fun conversations(page: Int, pageSize: Int): ApiResult<Paged<Conversation>> = readYourWrites.withLock {
        apiCall {
            chatApi.conversations(pageNum = page, pageSize = pageSize).unwrap()
        }.map { it.toConversationPage() }
    }

    /**
     * §12.3 历史消息。返回的 [Paged] 按时间**正序**（服务端按 id 升序分页），
     * [Paged.page] 越小越新——向上翻页请递减 page。
     */
    suspend fun messages(
        conversationId: Long,
        page: Int,
        pageSize: Int,
    ): ApiResult<Paged<ImMessage>> = apiCall {
        chatApi.messages(conversationId = conversationId, pageNum = page, pageSize = pageSize).unwrap()
    }.map { it.toMessagePage() }

    /**
     * §12.4 创建/获取会话（同一对用户幂等）。F2「私信」按钮点击时调用。
     * 对方是当前用户自己 → `8002`；对方不存在 → `1001`。
     */
    suspend fun ensureConversation(peerId: Long): ApiResult<Conversation> = apiCall {
        chatApi.createConversation(CreateConversationRequest(peerId = peerId)).unwrap()
    }.map { it.toDomain() }

    /**
     * §12.5 发送消息。[clientId] 由调用方生成并**在重试时保持不变**——
     * 服务端按（会话，发送者，clientId）幂等去重，重发返回同一条已落库消息。
     */
    suspend fun sendMessage(
        conversationId: Long,
        content: String,
        clientId: String,
    ): ApiResult<ImMessage> = apiCall {
        chatApi.sendMessage(
            conversationId = conversationId,
            body = SendMessageRequest(content = content, clientId = clientId),
        ).unwrap()
    }.map { it.toDomain() }

    /** §12.6 已读上报（该会话内对方发来且我未读的全部标已读）。与 [conversations] 串行（读跟随写）。 */
    suspend fun markRead(conversationId: Long): ApiResult<Long> = readYourWrites.withLock {
        apiCall {
            chatApi.markRead(conversationId = conversationId).unwrap().updated
        }
    }
}
