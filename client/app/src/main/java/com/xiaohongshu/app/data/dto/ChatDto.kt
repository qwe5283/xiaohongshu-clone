package com.xiaohongshu.app.data.dto

import kotlinx.serialization.Serializable

/**
 * `ConversationVO`（契约 §12.1）。
 *
 * 单聊会话：服务端按「两名成员」保证唯一（同一对用户复用同一会话）。
 * [peerId]/[peerNickname]/[peerAvatar] 是**对方**的视角字段——同一会话在不同登录用户
 * 眼里 peer 不同，由服务端按当前用户返回，客户端直接用即可。
 */
@Serializable
data class ConversationDto(
    val id: Long = 0,
    val peerId: Long = 0,
    val peerNickname: String = "",
    val peerAvatar: String = "",
    val lastMessageContent: String = "",
    /** `yyyy-MM-ddTHH:mm:ss`（契约时间格式）；服务端保证为最后一条消息的时间。 */
    val lastMessageTime: String = "",
    /** 对方发来且我未读的消息数；我自己发出的消息不计入。 */
    val unreadCount: Long = 0,
)

/** `ChatMessageVO`（契约 §12.1）。按 `id` 升序 = 时间升序（契约 §12.3）。 */
@Serializable
data class ChatMessageDto(
    val id: Long = 0,
    val conversationId: Long = 0,
    /** 发送者用户 id；与当前登录用户比对决定气泡左右。 */
    val senderId: Long = 0,
    val content: String = "",
    /** 1 = 文本（本版唯一取值；2/3 图片/表情预留）。 */
    val type: Int = 1,
    val createTime: String = "",
)

/** `POST /api/chat/conversations`（契约 §12.4）。 */
@Serializable
data class CreateConversationRequest(
    val peerId: Long,
)

/**
 * `POST /api/chat/{conversationId}/messages`（契约 §12.5）。
 *
 * [clientId] = 客户端生成的 uuid，服务端按（会话，发送者，clientId）幂等去重——
 * 重试时带同一个 clientId 返回同一条已落库消息，不会重复插入。
 */
@Serializable
data class SendMessageRequest(
    val content: String,
    val clientId: String,
)

/** `PUT /api/chat/{conversationId}/read`（契约 §12.6）。 */
@Serializable
data class ChatReadResultDto(
    val updated: Long = 0,
)
