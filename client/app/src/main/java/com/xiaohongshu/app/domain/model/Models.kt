package com.xiaohongshu.app.domain.model

import com.xiaohongshu.app.core.design.Dimens

/** 用户（UI 层模型）。 */
data class User(
    val id: Long,
    val username: String,
    val nickname: String,
    val avatar: String,
    /** 0-未知/保密，1-男，2-女。 */
    val gender: Int,
    val phone: String,
    val email: String,
    val bio: String,
    val backgroundImage: String,
    val birthday: String,
    val region: String,
    val occupation: String,
    val school: String,
    val redId: String,
    val followingCount: Long,
    val followersCount: Long,
    val likeAndCollectCount: Long,
    /** 我收藏的笔记数（F1 小组件卡「收藏」）。 */
    val collectedPostCount: Long,
    /** 我赞过的笔记数（F1 小组件卡「赞过」）。 */
    val likedPostCount: Long,
) {
    val displayName: String get() = nickname.ifBlank { username }
    val displayRedId: String get() = redId.ifBlank { id.toString() }
    val hasGender: Boolean get() = gender == 1 || gender == 2

    companion object {
        val Empty = User(
            id = 0, username = "", nickname = "", avatar = "", gender = 0,
            phone = "", email = "", bio = "", backgroundImage = "", birthday = "",
            region = "", occupation = "", school = "", redId = "",
            followingCount = 0, followersCount = 0, likeAndCollectCount = 0,
            collectedPostCount = 0, likedPostCount = 0,
        )
    }
}

/** 笔记图片。 */
data class NoteImage(
    val url: String,
    val width: Int,
    val height: Int,
) {
    /** 真实宽高比；后端未提供尺寸时回落到 3:4。 */
    val ratio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else Dimens.RATIO_TALL
}

/**
 * 笔记（UI 层模型）。
 *
 * [likeCount] / [collectCount] / [liked] / [collected] 等互动字段为「服务端返回值」，
 * 渲染时请经 [com.xiaohongshu.app.core.interact.InteractionStore] 合并本地乐观态，
 * 否则列表与详情会出现不一致。
 */
data class Note(
    val id: Long,
    val authorId: Long,
    val authorNickname: String,
    val authorAvatar: String,
    val title: String,
    val content: String,
    val isVideo: Boolean,
    val coverUrl: String,
    val videoUrl: String,
    val images: List<NoteImage>,
    val viewCount: Int,
    val likeCount: Int,
    /** = 一级评论数 + 回复数（与底栏 💬 一致）。 */
    val commentCount: Int,
    val collectCount: Int,
    val liked: Boolean = false,
    val collected: Boolean = false,
    val followed: Boolean = false,
    val createdAt: Long = 0L,
) {
    /**
     * 瀑布流封面比例。图笔记取首图真实比例，视频笔记无图片尺寸信息时用 3:4。
     * 线框要求混排 4:3 与 3:4，故不能写死。
     *
     * 极端比例（截长屏、全景图）会让卡片高于视口或压成细条，钳到线框两档之间。
     * 只收窄封面；[NoteImage.ratio] 保留真实值。
     */
    val coverRatio: Float
        get() = (images.firstOrNull()?.ratio ?: Dimens.RATIO_TALL)
            .coerceIn(Dimens.RATIO_TALL, Dimens.RATIO_WIDE)

    /** 详情大图列表；图笔记至少有 1 张（发布侧已禁止无素材笔记）。 */
    val detailImages: List<NoteImage>
        get() = if (images.isNotEmpty()) images else listOf(NoteImage(coverUrl, 0, 0))

    val authorLabel: String get() = authorNickname.ifBlank { "小红书用户" }
}

/** 评论。 */
data class Comment(
    val id: Long,
    val postId: Long,
    val userId: Long,
    val nickname: String,
    val avatar: String,
    val content: String,
    /** 0 = 一级评论。 */
    val parentId: Long,
    val replyUserId: Long,
    val replyUserNickname: String,
    val likeCount: Int,
    val liked: Boolean,
    /** 回复总数 —— 线框「展开 N 条回复」的 N（无上限）。 */
    val replyCount: Int,
    val createdAt: Long,
) {
    val isReply: Boolean get() = parentId != 0L

    /** 二级回复正文前缀：`回复 @昵称：内容`。 */
    val displayContent: String
        get() = if (replyUserNickname.isNotBlank()) "回复 @$replyUserNickname：$content" else content
}

/**
 * 一级评论下「回复组」的展开状态（C3-1/C3-4/C3-5）。
 *
 * 作为**独立于 [Comment] 的界面状态**由 ViewModel 持有（`Map<一级评论id, ReplyGroupState>`），
 * 这样 [Comment] 保持不可变、可直接参与状态比较，避免可变字段在 Compose 重组中丢更新。
 */
data class ReplyGroupState(
    /** 已就地平铺的回复（按时间正序，每批 10 条追加）。 */
    val replies: List<Comment> = emptyList(),
    /** 本批正在加载：按钮原位替换为转圈 +「加载中」（C3-4），不弹层不跳页。 */
    val loading: Boolean = false,
    /** 是否已点过展开（决定控件文案走「展开 N 条回复」还是「展开更多回复」）。 */
    val expanded: Boolean = false,
    /** 该组回复总数，来自一级评论的 `replyCount`。 */
    val total: Int = 0,
) {
    /** 全部加载完后控件直接消失、组尾衔接下一条评论——**无「收起」**。 */
    val allLoaded: Boolean get() = expanded && replies.size >= total && !loading

    /** 是否显示「展开 N 条回复 / 展开更多回复」控件。 */
    val canExpandMore: Boolean get() = !loading && replies.size < total

    companion object {
        val Idle = ReplyGroupState()
    }
}

/** 通知（G2/G3/G4 条目）。 */
data class NotificationItem(
    val id: Long,
    val senderId: Long,
    val senderNickname: String,
    val senderAvatar: String,
    val type: Int,
    val typeText: String,
    val postId: Long,
    val postCoverUrl: String,
    val commentId: Long,
    val content: String,
    val read: Boolean,
    val createdAt: Long,
) {
    /** 关注类通知没有关联笔记，不渲染缩略图。 */
    val hasThumbnail: Boolean get() = postCoverUrl.isNotBlank()
}

/** 分类未读数（底 Tab 角标 + G1 三入口角标）。 */
data class UnreadCounts(
    val total: Long = 0,
    val likeCollect: Long = 0,
    val comment: Long = 0,
    val follow: Long = 0,
) {
    val hasAny: Boolean get() = total > 0

    companion object {
        val Empty = UnreadCounts()
    }
}

/** 分页容器（UI 层）。[hasMore] 由 [page] 与 [pages] 推导，避免各列表各自计算。 */
data class Paged<T>(
    val items: List<T>,
    val page: Int,
    val total: Long,
    val pages: Long,
) {
    val hasMore: Boolean get() = page < pages

    companion object {
        fun <T> empty() = Paged<T>(emptyList(), 0, 0, 0)
    }
}

/** 点点对话消息（H1–H3）。仅内存保存，不做持久化。 */
data class ChatMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String = "",
    val notes: List<Note> = emptyList(),
    val state: ChatState = ChatState.Done,
) {
    enum class ChatState { Sending, Thinking, Done, Failed }
}

/** 搜索结果里「用户」Tab 的行（B3-1 的 Tab 之一）。 */
data class UserBrief(
    val id: Long,
    val nickname: String,
    val avatar: String,
    val bio: String,
)
