package com.xiaohongshu.app.feature.message

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohongshu.app.core.interact.CommentInteraction
import com.xiaohongshu.app.core.interact.InteractionStore
import com.xiaohongshu.app.core.list.PagedList
import com.xiaohongshu.app.core.list.PagedState
import com.xiaohongshu.app.core.net.ApiResult
import com.xiaohongshu.app.core.net.onFailure
import com.xiaohongshu.app.core.net.userMessage
import com.xiaohongshu.app.core.notify.UnreadCountCenter
import com.xiaohongshu.app.core.ui.ToastController
import com.xiaohongshu.app.data.dto.NotificationCategory
import com.xiaohongshu.app.data.dto.NotificationType
import com.xiaohongshu.app.data.repo.CommentRepository
import com.xiaohongshu.app.data.repo.NotificationRepository
import com.xiaohongshu.app.domain.model.Comment
import com.xiaohongshu.app.domain.model.NotificationItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * G3「回复」的行内展开状态（同一时刻只展开一行）。
 *
 * 为什么放在 ViewModel 而不是 `remember`：切到详情页再回来时，行内输入态不该回跳；
 * 且「发送中」要驱动该行的按钮态（I3 按钮级加载）。
 */
data class InlineReplyState(
    /** 展开输入框的条目 id；null = 无展开。 */
    val targetId: Long? = null,
    val text: String = "",
    val sending: Boolean = false,
) {
    val isOpen: Boolean get() = targetId != null

    companion object {
        val Idle = InlineReplyState()
    }
}

/** G2/G3/G4 列表 UI 状态。 */
data class NotificationListUiState(
    val category: NotificationCategory = NotificationCategory.LIKE_COLLECT,
    val page: PagedState<NotificationItem> = PagedState(),
    val reply: InlineReplyState = InlineReplyState(),
    /** D2：已关注（合并本地乐观态后）的发送者 id —— G4「回关 / 已关注」按此渲染。 */
    val followedSenders: Set<Long> = emptySet(),
    /** D1：已点赞（合并本地乐观态后）的评论 id —— G3 ♡ 按此渲染激活态。 */
    val likedCommentIds: Set<Long> = emptySet(),
)

/**
 * G2/G3/G4 通知列表的 ViewModel（一个 category 一份）。
 *
 * 约定与其它列表页一致：
 * - 分页走 [PagedList]（每页 20、距底预加载、四态、追加失败不清空）；
 * - 渲染数据来自**服务端返回值 + 本地乐观覆盖**（已读覆盖就地 mutate 到列表项上，
 *   点赞/关注态来自 [InteractionStore]）；
 * - 一次性反馈（Toast）用 [ToastController]，不进 state。
 */
class NotificationListViewModel(
    private val category: NotificationCategory,
    private val repository: NotificationRepository,
    private val comments: CommentRepository,
    private val interactions: InteractionStore,
    private val unreadCounts: UnreadCountCenter,
    private val toasts: ToastController,
) : ViewModel() {

    private val list = PagedList<NotificationItem>(
        keyOf = { it.id },
        toasts = toasts,
        fetch = { page, size -> repository.list(category, page, size) },
    )

    /** 供 UI 挂载分页副作用（`PagedListEffect` 需要具体的 [PagedList] 实例）。 */
    val paged: PagedList<NotificationItem> get() = list

    private val _reply = MutableStateFlow(InlineReplyState.Idle)

    val state: StateFlow<NotificationListUiState> = combine(
        list.state,
        _reply,
        interactions.followOverrides,
        interactions.commentOverrides,
    ) { page, reply, follows, commentLikes ->
        NotificationListUiState(
            category = category,
            page = page,
            reply = reply,
            followedSenders = followedSendersOf(page.items, follows),
            likedCommentIds = likedCommentIdsOf(page.items, commentLikes),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = NotificationListUiState(category = category),
    )

    // ------------------------------------------------------------------ 分页四态（G5-1/2/3）

    /** G5-3 首次失败后的「重试」。 */
    fun retry() {
        viewModelScope.launch { list.retry() }
    }

    // ------------------------------------------------------------------ 条目：先标已读 → 再跳转

    /**
     * 点条目（G2/G3/G4）。
     *
     * **先标已读**（本地乐观置已读 + 角标 -1），**再**由 UI 侧跳 C1-1；
     * 请求本身失败不阻断操作、也不弹错——线框 G5-2「已读标记失败不阻断操作，下次轮询校正」。
     * （`PagedList` 的 errors 只覆盖分页请求，故这里手动忽略返回值。）
     */
    fun onRowClick(item: NotificationItem) {
        markRead(item)
    }

    /** G6 一键已读：当前列表全部置已读 + 本分类角标清零。 */
    fun markAllRead() {
        list.mutate { items -> items.map { if (it.read) it else it.copy(read = true) } }
        unreadCounts.clearCategory(category.value)
        viewModelScope.launch {
            repository.markAllRead(category).onFailure { toasts.show(it.userMessage()) }
            // 失败时本地已乐观清零，角标由下一次 15s 轮询校正（G5-2 同款异常恢复）
        }
    }

    // ------------------------------------------------------------------ G4 回关（D2）

    /**
     * G4「回关」：走 D2 同一状态机（[InteractionStore.toggleFollow]），并**自动标记已读**。
     *
     * 服务端基准值说明：`NotificationItem` 没有「我是否已关注对方」字段，而本类通知的语义就是
     * 「对方刚开始关注我」（此时我尚未关注 ta），故基准固定传 `false`；
     * 合并后的展示值走 `InteractionStore.followedOf(senderId, false)`（见 [followedSendersOf]），
     * 与详情页/他人主页读写同一份覆盖，跨页一致。
     */
    fun toggleFollowBack(item: NotificationItem) {
        viewModelScope.launch {
            interactions.toggleFollow(item.senderId, serverValue = false)
        }
        markRead(item)
    }

    // ------------------------------------------------------------------ G3 ♡ 点赞该条评论（D1）

    /**
     * G3 ♡：乐观更新 + 失败回滚由 [InteractionStore] 负责。
     *
     * **传入的是 raw 值**（[rawCommentOf] 直接从通知条目构造，`liked = false` / `likeCount = 0` 是
     * 服务端基准），绝不用 `merge()` 的结果回喂 —— 否则增量会被重复叠加（开发规范 §4.3 的警告）。
     */
    fun toggleCommentLike(item: NotificationItem) {
        val raw = rawCommentOf(item) ?: return
        viewModelScope.launch { interactions.toggleCommentLike(raw) }
    }

    // ------------------------------------------------------------------ G3 行内回复

    fun openReply(item: NotificationItem) {
        _reply.value = InlineReplyState(targetId = item.id)
    }

    fun closeReply() {
        _reply.value = InlineReplyState.Idle
    }

    /** 评论正文 ≤500（契约 §3.1），超出部分直接截断。 */
    fun onReplyChange(text: String) {
        _reply.value = _reply.value.copy(text = text.take(MAX_REPLY_LENGTH))
    }

    /**
     * G3「发送」：成功后 Toast「回复成功」+ 自动已读 + 收起输入框；失败保留输入内容可重试。
     *
     * 字段映射（模型字段不足处，按交付说明的约定映射，未新增契约字段）：
     * `NotificationItem` 只为评论/回复类通知携带 `postId` / `commentId`（触发通知的那条评论）与
     * `senderId`（评论者）。契约 §3.1 要求 `parentId` = 一级评论 id、`replyUserId` = 被回复者 id，
     * 故此处用 **`commentId` → `parentId`**、**`senderId` → `replyUserId`**，
     * 语义即「在该通知对应的评论下回复该通知的发送者」。
     */
    fun sendReply(item: NotificationItem) {
        val text = _reply.value.text.trim()
        val current = _reply.value
        if (current.sending || current.targetId != item.id || text.isBlank()) return
        val parentId = item.commentId
        if (item.postId <= 0 || parentId <= 0) {
            toasts.show("该消息无法回复")
            return
        }
        _reply.value = current.copy(sending = true)
        viewModelScope.launch {
            val result = comments.createReply(
                postId = item.postId,
                parentId = parentId,
                replyUserId = item.senderId,
                content = text,
            )
            if (result is ApiResult.Ok) {
                toasts.show("回复成功")
                markRead(item)
                _reply.value = InlineReplyState.Idle
            } else {
                // 失败保留输入内容，输入框恢复可用（I2：Toast 反馈）
                toasts.show(result.userMessage())
                _reply.value = current.copy(sending = false)
            }
        }
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 通知条目 → 评论（D1 点赞的服务端基准值）。
     * 通知模型只带 `commentId`/`content`/`senderId`，无 `likeCount`/`liked`，
     * 故基准取 `liked = false, likeCount = 0`；展示值由 [InteractionStore.merge] 合并得到。
     * 关注类通知（type = 6）没有评论，返回 null。
     */
    private fun rawCommentOf(item: NotificationItem): Comment? {
        if (item.commentId <= 0) return null
        return Comment(
            id = item.commentId,
            postId = item.postId,
            userId = item.senderId,
            nickname = item.senderNickname,
            avatar = item.senderAvatar,
            content = item.content,
            parentId = 0,
            replyUserId = 0,
            replyUserNickname = "",
            likeCount = 0,
            liked = false,
            replyCount = 0,
            createdAt = item.createdAt,
        )
    }

    /** 「先标已读 → 再跳转」里的第一步：本地乐观置已读 + 角标 -1 + 发请求（失败静默）。 */
    private fun markRead(item: NotificationItem) {
        if (item.read) return
        list.mutate { items -> items.map { if (it.id == item.id) it.copy(read = true) else it } }
        unreadCounts.decrement(category.value)
        viewModelScope.launch { repository.markRead(item.id) }
    }

    private companion object {
        /** 离开页面后保留订阅 5s，避免短暂切页导致重新拉取。 */
        const val STOP_TIMEOUT_MS = 5_000L

        /** 评论正文上限（契约 §3.1）。 */
        const val MAX_REPLY_LENGTH = 500
    }
}

/** G4：按「关注类通知的发送者」过滤出已关注的 id（`follows` 为本地覆盖表）。 */
private fun followedSendersOf(
    items: List<NotificationItem>,
    follows: Map<Long, Boolean>,
): Set<Long> = items.asSequence()
    .filter { it.type == NotificationType.FOLLOW_USER }
    .map { it.senderId }
    .filter { follows[it] == true }
    .toSet()

/** G3：按本地覆盖表算出「已点赞」的 commentId 集合，供 ♡ 渲染激活态。 */
private fun likedCommentIdsOf(
    items: List<NotificationItem>,
    commentLikes: Map<Long, CommentInteraction>,
): Set<Long> = items.asSequence()
    .map { it.commentId }
    .filter { it > 0 && commentLikes[it]?.liked == true }
    .toSet()
