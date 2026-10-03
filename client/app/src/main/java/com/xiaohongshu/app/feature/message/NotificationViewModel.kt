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
 * G3「回复」的遮罩输入状态（C1-5 `XhsOverlayInputBar` 同款；同一时刻只弹一个）。
 *
 * 为什么放在 ViewModel 而不是 `remember`：切到详情页再回来时遮罩不该闪回；
 * 「发送中」要驱动发送钮禁用（I3），失败要回填已输入内容（D3 保留）。
 */
data class ReplyOverlayState(
    /** 弹遮罩的通知条目；null = 关闭。 */
    val item: NotificationItem? = null,
    /** 发送失败回填：写回已输入内容，保证 D3「失败内容保留」。 */
    val initialText: String = "",
    val sending: Boolean = false,
) {
    val isOpen: Boolean get() = item != null

    /** 占位与 C1-5 同款「回复 @昵称：」。 */
    val placeholder: String get() = "回复 @${item?.senderNickname.orEmpty()}："

    companion object {
        val Idle = ReplyOverlayState()
    }
}

/** G2/G3/G4 列表 UI 状态。 */
data class NotificationListUiState(
    val category: NotificationCategory = NotificationCategory.LIKE_COLLECT,
    val page: PagedState<NotificationItem> = PagedState(),
    val replyOverlay: ReplyOverlayState = ReplyOverlayState(),
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

    private val _replyOverlay = MutableStateFlow(ReplyOverlayState.Idle)

    val state: StateFlow<NotificationListUiState> = combine(
        list.state,
        _replyOverlay,
        interactions.followOverrides,
        interactions.commentOverrides,
    ) { page, replyOverlay, follows, commentLikes ->
        NotificationListUiState(
            category = category,
            page = page,
            replyOverlay = replyOverlay,
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
     * 请求本身失败不阻断操作、也不弹错——G5-2「已读标记失败不阻断操作，下次轮询校正」。
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
        interactions.toggleFollow(item.senderId, serverValue = false)
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
        interactions.toggleCommentLike(raw)
    }

    // ------------------------------------------------------------------ G3 遮罩回复（C1-5 同款）

    fun openReply(item: NotificationItem) {
        _replyOverlay.value = ReplyOverlayState(item = item)
    }

    fun dismissReply() {
        _replyOverlay.value = ReplyOverlayState.Idle
    }

    /**
     * G3「发送」：成功后 Toast「回复成功」+ 自动已读 + 收起遮罩；失败回填已输入内容可重试（D3）。
     *
     * 字段映射（契约 §3.1）：`parentId` = 一级评论 id、`replyUserId` = 被回复者 id，
     * 此处用 **`commentId` → `parentId`**、**`senderId` → `replyUserId`**。
     */
    fun sendReply(text: String) {
        // 遮罩不带字数计数（对齐 C1-5），发送前按契约 §3.1 上限截断
        val content = text.trim().take(MAX_REPLY_LENGTH)
        val current = _replyOverlay.value
        val item = current.item ?: return
        if (current.sending || content.isBlank()) return
        val parentId = item.commentId
        if (item.postId <= 0 || parentId <= 0) {
            toasts.show("该消息无法回复")
            return
        }
        _replyOverlay.value = current.copy(sending = true)
        viewModelScope.launch {
            val result = comments.createReply(
                postId = item.postId,
                parentId = parentId,
                replyUserId = item.senderId,
                content = content,
            )
            if (result is ApiResult.Ok) {
                toasts.show("回复成功")
                markRead(item)
                _replyOverlay.value = ReplyOverlayState.Idle
            } else {
                // 失败回填已输入内容，遮罩保持打开可重试（I2：Toast 反馈）
                toasts.show(result.userMessage())
                _replyOverlay.value = current.copy(sending = false, initialText = content)
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
