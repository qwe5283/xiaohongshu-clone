package com.xiaohongshu.app.data.dto

import kotlinx.serialization.Serializable

/**
 * `NotificationVO`（client/docs/API契约-v2.md §7.5）。
 *
 * [type] 取值：1-点赞笔记，2-收藏笔记，3-评论笔记，4-回复评论，5-点赞评论，6-新增关注。
 */
@Serializable
data class NotificationDto(
    val id: Long = 0,
    val receiverId: Long = 0,
    val senderId: Long = 0,
    val senderNickname: String = "",
    val senderAvatar: String = "",
    val type: Int = 0,
    /** 后端可选返回的中文描述，为空时客户端按 [type] 本地兜底。 */
    val typeText: String = "",
    val postId: Long = 0,
    val postTitle: String = "",
    val postCoverImage: String = "",
    val commentId: Long = 0,
    val content: String = "",
    /** 是否已读（后端 VO 字段名为 `read`）。 */
    val read: Boolean = false,
    val createTime: String = "",
)

/** `GET /api/notification/unread-count`（契约变更 #6：必须返回分类未读数）。 */
@Serializable
data class UnreadCountDto(
    /** 总数 → 底 Tab 角标（>99 显示 99+）。 */
    val unreadCount: Long = 0,
    /** G1「赞和收藏」入口角标（type ∈ {1,2,5}）。 */
    val likeUnread: Long = 0,
    /** G1「评论和@」入口角标（type ∈ {3,4}）。 */
    val commentUnread: Long = 0,
    /** G1「新增关注」入口角标（type = 6）。 */
    val followUnread: Long = 0,
)

/**
 * 通知入口分类 —— `GET /api/notification/list?category=`（契约变更 #7）。
 *
 * 线框 G1 三入口 → G2/G3/G4 三个列表页。
 */
enum class NotificationCategory(val value: Int) {
    /** 赞和收藏（G2）：type ∈ {1,2,5}。 */
    LIKE_COLLECT(1),

    /** 评论和@（G3）：type ∈ {3,4}。 */
    COMMENT(2),

    /** 新增关注（G4）：type = 6。 */
    FOLLOW(3),
}

/** 通知细分类型常量。 */
object NotificationType {
    const val LIKE_POST = 1
    const val COLLECT_POST = 2
    const val COMMENT_POST = 3
    const val REPLY_COMMENT = 4
    const val LIKE_COMMENT = 5
    const val FOLLOW_USER = 6

    /** [typeText] 为空时的客户端本地兜底文案（G2/G3/G4）。 */
    fun fallbackText(type: Int): String = when (type) {
        LIKE_POST -> "赞了你的笔记"
        COLLECT_POST -> "收藏了你的笔记"
        COMMENT_POST -> "评论了你的笔记"
        REPLY_COMMENT -> "回复了你的评论"
        LIKE_COMMENT -> "赞了你的评论"
        FOLLOW_USER -> "关注了你"
        else -> "有新动态"
    }
}
