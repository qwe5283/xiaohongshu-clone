package com.xiaohongshu.app.data.dto

import kotlinx.serialization.Serializable

/** `CommentVO`（client/docs/API契约-v2.md §3.5）。 */
@Serializable
data class CommentDto(
    val id: Long = 0,
    val postId: Long = 0,
    val userId: Long = 0,
    val userNickname: String = "",
    val userAvatar: String = "",
    val content: String = "",
    /** 0 = 一级评论。 */
    val parentId: Long = 0,
    val replyUserId: Long = 0,
    val replyUserNickname: String = "",
    val likeCount: Int = 0,
    val liked: Boolean = false,
    /** 回复总数（仅一级评论有值），即线框「展开 N 条回复」的 N，无上限。 */
    val replyCount: Int = 0,
    val createTime: String = "",
)

/** `POST /api/comment/create` 请求体。 */
@Serializable
data class CreateCommentRequest(
    val postId: Long,
    val content: String,
    /** 0 = 一级评论。 */
    val parentId: Long = 0,
    /** 一级评论为 0；回复时为被回复者 userId。 */
    val replyUserId: Long = 0,
)
