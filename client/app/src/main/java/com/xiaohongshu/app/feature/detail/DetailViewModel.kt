package com.xiaohongshu.app.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohongshu.app.core.interact.InteractionStore
import com.xiaohongshu.app.core.list.PagedList
import com.xiaohongshu.app.core.net.ApiResult
import com.xiaohongshu.app.core.net.userMessage
import com.xiaohongshu.app.core.ui.ToastController
import com.xiaohongshu.app.data.local.SessionManager
import com.xiaohongshu.app.data.repo.CommentRepository
import com.xiaohongshu.app.data.repo.FollowRepository
import com.xiaohongshu.app.data.repo.PostRepository
import com.xiaohongshu.app.domain.model.Comment
import com.xiaohongshu.app.domain.model.Note
import com.xiaohongshu.app.domain.model.ReplyGroupState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/*
 * C1/C2/C3 的状态与动作。
 *
 * 三个关键约定（决定了这里的形状）：
 * 1. **渲染用合并值、提交用服务端快照**：互动计数一律经 InteractionStore.merge 渲染，
 *    但调用 toggleLike/toggleCollect/toggleCommentLike 时必须传**服务端原始对象**
 *    （store 的 delta 是「服务端计数 + 增量」，传合并值会让 deltaFor 算出 0，计数不 +1）。
 * 2. **写操作只做结果**：评论/回复/互动提交前的登录拦截（loginGate）由 Route 层负责（§4.4）。
 * 3. **回复展开态是独立于 Comment 的界面状态**：`Map<一级评论 id, ReplyGroupState>`，
 *    由 [CommentsController] 持有，图文页流与视频评论面板共用同一份实现。
 */

/** 详情请求三态。[raw] 为服务端快照（互动提交以它为基准）。 */
internal data class DetailLoadState(
    val loading: Boolean = true,
    val error: String? = null,
    val raw: Note? = null,
    val serverFollowed: Boolean = false,
)

/** 服务端快照 + 本地乐观覆盖（渲染用）。 */
internal data class DetailDisplay(
    val load: DetailLoadState,
    val note: Note?,
    val followed: Boolean,
    val isSelf: Boolean,
)

/** 一次评论列表的可观察结果（[items] 已合并评论 ♥ 的乐观覆盖）。 */
internal data class CommentsUiState(
    val items: List<Comment> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
    val endReached: Boolean = false,
) {
    /** C1-4/C3-2 空态。 */
    val isEmpty: Boolean get() = loaded && error == null && items.isEmpty()

    val hasContent: Boolean get() = items.isNotEmpty()
}

/** 图文详情页面本地状态：D3 计数增量 + 底部输入栏草稿 / 回复目标（C1-5）。 */
internal data class NoteLocalState(
    /** 本次会话新增的一级评论 / 回复数（「共 n 条评论」与底栏 💬 同步 +1）。 */
    val addedCount: Int = 0,
    /** 底部输入栏是否处于输入态（点胶囊聚焦，D3）。 */
    val composing: Boolean = false,
    val draft: String = "",
    /** C1-5 的被回复对象（null = 发一级评论）。 */
    val replyTarget: Comment? = null,
    /** 回复目标所属的一级评论（嵌套回复时 parentId 必须指向一级评论）。 */
    val replyParent: Comment? = null,
    val sending: Boolean = false,
)

internal data class NoteDetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val note: Note? = null,
    val followed: Boolean = false,
    /** 自己的笔记不显示关注按钮（C1-1 / D2）。 */
    val isSelf: Boolean = false,
    /** 「共 n 条评论」/ 底栏 💬 / C3 Tab「评论 n」的 n（含回复，D3 本地 +1）。 */
    val commentTotal: Int = 0,
    val comments: CommentsUiState = CommentsUiState(),
    val replyGroups: Map<Long, ReplyGroupState> = emptyMap(),
    val composing: Boolean = false,
    val draft: String = "",
    val replyTarget: Comment? = null,
    val sending: Boolean = false,
)

/** 遮罩式输入（C2-4 / C3-3）状态。 */
internal data class OverlayInputState(
    val visible: Boolean = false,
    val placeholder: String = "",
    /** 失败回填用：写回已输入内容，保证 D3「失败内容保留」。 */
    val initialText: String = "",
    val replyParent: Comment? = null,
    val replyTarget: Comment? = null,
    val sending: Boolean = false,
)

/** 视频详情页面本地状态（面板 / 遮罩输入 / D3 计数增量）。 */
internal data class VideoLocalState(
    val addedCount: Int = 0,
    val panelOpen: Boolean = false,
    val panelDraft: String = "",
    val panelSending: Boolean = false,
    val overlay: OverlayInputState = OverlayInputState(),
)

internal data class VideoDetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val note: Note? = null,
    val followed: Boolean = false,
    val isSelf: Boolean = false,
    val commentTotal: Int = 0,
    val comments: CommentsUiState = CommentsUiState(),
    val replyGroups: Map<Long, ReplyGroupState> = emptyMap(),
    /** C3-1 面板是否打开（打开时媒体区上移缩小、底栏由面板接管）。 */
    val panelOpen: Boolean = false,
    val panelDraft: String = "",
    val panelSending: Boolean = false,
    val overlay: OverlayInputState = OverlayInputState(),
)

/**
 * 评论区控制器：一级评论分页（每页 20，距底自动加载）+ 回复组展开状态机（C3-1/C3-4/C3-5）。
 *
 * 回复展开规则（严格按线框）：
 * - 折叠时 `—— 展开 N 条回复`，N = 该组 `replyCount`（无上限）；
 * - 点击后**原位**变「⟳ 加载中」，不弹层不跳页；
 * - 每批拉取 `CommentRepository.BATCH_SIZE` = 10 条，就地平铺；
 * - 未加载完 → 组尾 `—— 展开更多回复`（无数字）；
 * - 全部加载完 → 控件**直接消失**（无「收起」），组尾衔接下一条评论；
 * - 失败 → 全局 Toast + 恢复控件（第一批回到「展开 N 条回复」）可重试；
 * - 面板关闭重开 / 详情重试 → [resetGroups] 展开态重置为折叠。
 */
internal class CommentsController(
    private val postId: Long,
    private val repository: CommentRepository,
    private val store: InteractionStore,
    private val toasts: ToastController,
    private val scope: CoroutineScope,
) {

    private val firstLevel = PagedList<Comment>(
        pageSize = CommentRepository.FIRST_LEVEL_PAGE_SIZE,
        keyOf = { it.id },
        toasts = toasts,
        fetch = { page, size -> repository.firstLevel(postId, page, size) },
    )

    /** 一级评论列表（渲染值已合并评论 ♥ 的乐观态）。 */
    val state: StateFlow<CommentsUiState> =
        combine(firstLevel.state, store.commentOverrides) { paged, _ ->
            CommentsUiState(
                items = paged.items.map { store.merge(it) },
                loading = paged.loading,
                loadingMore = paged.loadingMore,
                error = paged.error,
                loaded = paged.loaded,
                endReached = paged.endReached,
            )
        }.stateIn(scope, SharingStarted.Eagerly, CommentsUiState(loading = true))

    private val _groups = MutableStateFlow<Map<Long, ReplyGroupState>>(emptyMap())

    /**
     * 回复组展开态（key = 一级评论 id）。楼中楼回复的 ♥ 与一级评论共用同一份
     * 乐观覆盖，故 [ReplyGroupState.replies] 渲染前也要经 store 合并——否则点赞
     * 请求成功但图标停在服务端旧值。内部状态机仍读写 [_groups] 的服务端原始
     * 快照（[rawComment] 的提交基准，见类头约定 1）。
     */
    val groups: StateFlow<Map<Long, ReplyGroupState>> =
        combine(_groups, store.commentOverrides) { g, _ ->
            g.mapValues { it.value.copy(replies = store.mergeComments(it.value.replies)) }
        }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** 每组已拉取的批次数：只用于推进「展开更多回复」的页码，不参与渲染。 */
    private val loadedBatches = mutableMapOf<Long, Int>()

    suspend fun refresh() {
        resetGroups()
        firstLevel.refresh()
    }

    suspend fun retry() {
        resetGroups()
        firstLevel.retry()
    }

    suspend fun loadMore() = firstLevel.loadMore()

    /** 展开态重置为折叠（C3-5：面板关闭后重开列表整体重载）。 */
    fun resetGroups() {
        loadedBatches.clear()
        _groups.value = emptyMap()
    }

    /**
     * 「展开 N 条回复」与「展开更多回复」共用：拉取下一批 10 条。
     */
    fun expandGroup(parent: Comment) {
        val current = _groups.value[parent.id] ?: ReplyGroupState(total = parent.replyCount)
        if (current.loading) return
        // 全部加载完：控件已消失，无「收起」，不再有动作
        if (current.expanded && current.replies.size >= current.total) return

        val baseTotal = maxOf(current.total, parent.replyCount)
        putGroup(parent.id, current.copy(loading = true, total = baseTotal))

        scope.launch {
            val nextPage = (loadedBatches[parent.id] ?: 0) + 1
            when (val result = repository.replies(parent.id, nextPage, CommentRepository.BATCH_SIZE)) {
                is ApiResult.Ok -> {
                    loadedBatches[parent.id] = nextPage
                    val page = result.data
                    val merged = (current.replies + page.items).distinctBy { it.id }
                    // 还有下一页 → 保留「展开更多回复」；否则 total 收敛到已加载数 → 控件消失
                    val total = if (page.hasMore) {
                        maxOf(baseTotal, page.total.toInt(), merged.size + 1)
                    } else {
                        merged.size
                    }
                    putGroup(
                        parent.id,
                        ReplyGroupState(
                            replies = merged,
                            loading = false,
                            expanded = true,
                            total = total,
                        ),
                    )
                }
                else -> {
                    putGroup(parent.id, current.copy(loading = false))
                    toasts.show(result.userMessage())
                }
            }
        }
    }

    /** 一级评论 ♥（D1）。[commentId] 一定是服务端快照里的 id。 */
    fun toggleCommentLike(commentId: Long) {
        val raw = rawComment(commentId) ?: return
        scope.launch { store.toggleCommentLike(raw) }
    }

    /** D3 一级评论：插入列表顶部。 */
    suspend fun sendFirstLevel(content: String): Boolean =
        when (val result = repository.createFirstLevel(postId, content)) {
            is ApiResult.Ok -> {
                firstLevel.prepend(result.data)
                true
            }
            else -> {
                toasts.show(result.userMessage())
                false
            }
        }

    /**
     * D3 回复：追加到父评论并自动展开；父评论 `replyCount` +1。
     *
     * [parent] 必须是**一级评论**（嵌套回复也归到该组）；[replyUserId] 为被回复者。
     */
    suspend fun sendReply(
        parent: Comment,
        replyUserId: Long,
        replyNickname: String,
        content: String,
    ): Boolean = when (val result = repository.createReply(postId, parent.id, replyUserId, content)) {
        is ApiResult.Ok -> {
            val created = if (result.data.replyUserNickname.isBlank()) {
                result.data.copy(replyUserNickname = replyNickname)
            } else {
                result.data
            }
            // 「展开 N 条回复」的 N 随之上移
            firstLevel.mutate { list ->
                list.map { if (it.id == parent.id) it.copy(replyCount = it.replyCount + 1) else it }
            }
            val before = _groups.value[parent.id] ?: ReplyGroupState(total = parent.replyCount)
            val replies = (before.replies + created).distinctBy { it.id }
            putGroup(
                parent.id,
                ReplyGroupState(
                    replies = replies,
                    loading = false,
                    expanded = true,
                    total = maxOf(before.total + 1, replies.size),
                ),
            )
            true
        }
        else -> {
            toasts.show(result.userMessage())
            false
        }
    }

    private fun putGroup(parentId: Long, value: ReplyGroupState) {
        _groups.value = _groups.value + (parentId to value)
    }

    /** 服务端原始评论（store 的增量以服务端快照为基准，不能传合并后的值）。 */
    private fun rawComment(id: Long): Comment? =
        firstLevel.state.value.items.firstOrNull { it.id == id }
            ?: _groups.value.values
                .asSequence()
                .flatMap { it.replies.asSequence() }
                .firstOrNull { it.id == id }
}

/**
 * C1/C2 详情共用的笔记加载与互动（D1/D2）。
 *
 * 不持有页面本地状态（图文 / 视频的输入栏与面板不同），只负责：
 * 详情三态、关注初值、点赞/收藏/关注/评论点赞四个动作、评论区控制器。
 */
internal abstract class DetailViewModel(
    protected val postId: Long,
    private val postRepository: PostRepository,
    commentRepository: CommentRepository,
    private val followRepository: FollowRepository,
    protected val interactionStore: InteractionStore,
    private val sessionManager: SessionManager,
    protected val toasts: ToastController,
) : ViewModel() {

    protected val load = MutableStateFlow(DetailLoadState())

    /** 评论区（C1 页面流 / C3 面板共用）。 */
    val comments = CommentsController(postId, commentRepository, interactionStore, toasts, viewModelScope)

    /** 渲染值：服务端快照 + InteractionStore 乐观覆盖。 */
    protected val display: StateFlow<DetailDisplay> =
        combine(load, interactionStore.noteOverrides, interactionStore.followOverrides) { l, _, _ ->
            DetailDisplay(
                load = l,
                note = l.raw?.let { interactionStore.merge(it) },
                followed = l.raw?.let { interactionStore.followedOf(it.authorId, l.serverFollowed) } ?: false,
                isSelf = l.raw != null && l.raw.authorId == sessionManager.currentUserId,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, DetailDisplay(DetailLoadState(), null, false, false))

    protected fun loadDetail() {
        load.value = DetailLoadState(loading = true)
        viewModelScope.launch {
            when (val result = postRepository.detail(postId)) {
                is ApiResult.Ok -> {
                    load.value = DetailLoadState(
                        loading = false,
                        raw = result.data,
                        serverFollowed = result.data.followed,
                    )
                    syncFollowState(result.data)
                }
                else -> load.value = DetailLoadState(loading = false, error = result.userMessage())
            }
        }
    }

    /** C1-3「重试」/ C2-3 返回重进。 */
    protected fun retryDetail() = loadDetail()

    /**
     * D2 初次关注态：详情自带 `followed`，登录态下再用 `/api/follow/status` 校正。
     * 游客**不调** 🔒 接口（401 会触发全局会话失效跳登录，A2 只应发生在用户点击写操作时）。
     */
    private suspend fun syncFollowState(note: Note) {
        if (!sessionManager.isLoggedIn) return
        if (note.authorId == sessionManager.currentUserId) return
        when (val result = followRepository.followed(note.authorId)) {
            is ApiResult.Ok -> if (load.value.raw?.id == note.id) {
                load.value = load.value.copy(serverFollowed = result.data)
            }
            else -> Unit
        }
    }

    // ------------------------------------------------------------------ D1 / D2

    fun toggleLike() {
        val raw = load.value.raw ?: return
        viewModelScope.launch { interactionStore.toggleLike(raw) }
    }

    fun toggleCollect() {
        val raw = load.value.raw ?: return
        viewModelScope.launch {
            interactionStore.toggleCollect(raw)
            // 收藏集合变化 → 通知 F1「收藏」Tab 刷新
            interactionStore.invalidateCollections()
        }
    }

    fun toggleFollow() {
        val raw = load.value.raw ?: return
        if (raw.authorId == sessionManager.currentUserId) return
        viewModelScope.launch { interactionStore.toggleFollow(raw.authorId, load.value.serverFollowed) }
    }

    fun toggleCommentLike(commentId: Long) = comments.toggleCommentLike(commentId)

    fun expandReplies(parent: Comment) = comments.expandGroup(parent)

    fun loadMoreComments() {
        viewModelScope.launch { comments.loadMore() }
    }
}

/** C1-1~C1-5 图文详情。 */
internal class NoteDetailViewModel(
    postId: Long,
    postRepository: PostRepository,
    commentRepository: CommentRepository,
    followRepository: FollowRepository,
    interactionStore: InteractionStore,
    sessionManager: SessionManager,
    toasts: ToastController,
) : DetailViewModel(
    postId, postRepository, commentRepository, followRepository, interactionStore, sessionManager, toasts,
) {

    private val local = MutableStateFlow(NoteLocalState())

    val state: StateFlow<NoteDetailUiState> =
        combine(display, comments.state, comments.groups, local) { d, cs, groups, l ->
            NoteDetailUiState(
                loading = d.load.loading,
                error = d.load.error,
                note = d.note,
                followed = d.followed,
                isSelf = d.isSelf,
                // 契约 #13：commentCount = 一级评论 + 回复；D3 本地新增再 +1
                commentTotal = (d.load.raw?.commentCount ?: 0) + l.addedCount,
                comments = cs,
                replyGroups = groups,
                composing = l.composing,
                draft = l.draft,
                replyTarget = l.replyTarget,
                sending = l.sending,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, NoteDetailUiState())

    init {
        loadDetail()
        // C1-2：详情与首屏评论**并行**请求
        viewModelScope.launch { comments.refresh() }
    }

    fun retry() = retryDetail()

    /** 评论区首屏失败后的「重试」（追加失败只弹全局 Toast，不清空已加载内容）。 */
    fun retryComments() {
        viewModelScope.launch { comments.retry() }
    }

    /** D3：点底部 / 评论区胶囊 → 聚焦底部输入栏发一级评论（作答回复态一并复位）。 */
    fun startComposing() {
        local.value = local.value.copy(composing = true, replyTarget = null, replyParent = null)
    }

    fun cancelComposing() {
        local.value = local.value.copy(composing = false, replyTarget = null, replyParent = null)
    }

    fun onDraftChange(text: String) {
        local.value = local.value.copy(draft = text)
    }

    /**
     * C1-5：点评论「回复」→ 底部输入栏占位变「回复 @昵称：」；
     * 再次点同一条「回复」→ 取消并恢复「说点什么...」。
     * 图文回复**不弹遮罩面板**（与视频 C2-4/C3-3 刻意区分）。
     */
    fun toggleReply(parent: Comment, target: Comment) {
        val current = local.value
        local.value = if (current.replyTarget?.id == target.id) {
            current.copy(replyTarget = null, replyParent = null, composing = false)
        } else {
            current.copy(replyTarget = target, replyParent = parent, composing = true)
        }
    }

    /** D3：发送一级评论或回复。成功清空输入；失败保留内容（错误由全局 Toast 呈现）。 */
    fun send() {
        val current = local.value
        val content = current.draft.trim()
        if (content.isEmpty() || current.sending) return

        local.value = current.copy(sending = true)
        viewModelScope.launch {
            val parent = current.replyParent
            val target = current.replyTarget
            val ok = if (parent != null && target != null) {
                val done = comments.sendReply(parent, target.userId, target.nickname, content)
                if (done) toasts.show("回复成功")
                done
            } else {
                comments.sendFirstLevel(content)
            }
            val latest = local.value
            local.value = latest.copy(
                sending = false,
                addedCount = if (ok) latest.addedCount + 1 else latest.addedCount,
                draft = if (ok) "" else latest.draft,
            )
        }
    }
}

/** C2-1~C2-5 视频详情（评论面板 C3-1~C3-5 由本 ViewModel 驱动）。 */
internal class VideoDetailViewModel(
    postId: Long,
    postRepository: PostRepository,
    commentRepository: CommentRepository,
    followRepository: FollowRepository,
    interactionStore: InteractionStore,
    sessionManager: SessionManager,
    toasts: ToastController,
) : DetailViewModel(
    postId, postRepository, commentRepository, followRepository, interactionStore, sessionManager, toasts,
) {

    private val local = MutableStateFlow(VideoLocalState())

    val state: StateFlow<VideoDetailUiState> =
        combine(display, comments.state, comments.groups, local) { d, cs, groups, l ->
            VideoDetailUiState(
                loading = d.load.loading,
                error = d.load.error,
                note = d.note,
                followed = d.followed,
                isSelf = d.isSelf,
                commentTotal = (d.load.raw?.commentCount ?: 0) + l.addedCount,
                comments = cs,
                replyGroups = groups,
                panelOpen = l.panelOpen,
                panelDraft = l.panelDraft,
                panelSending = l.panelSending,
                overlay = l.overlay,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, VideoDetailUiState())

    init {
        // 评论只在面板打开时加载（C3-1）；媒体区不内嵌评论流
        loadDetail()
    }

    fun retry() = retryDetail()

    /** C3-1：点「💬」打开面板。关闭后重开 → 列表整体重载、展开态重置、滚动回顶部。 */
    fun openComments() {
        local.value = local.value.copy(panelOpen = true, overlay = OverlayInputState())
        viewModelScope.launch { comments.refresh() }
    }

    fun closeComments() {
        local.value = local.value.copy(
            panelOpen = false,
            panelDraft = "",
            overlay = OverlayInputState(),
        )
        comments.resetGroups()
    }

    fun retryComments() {
        viewModelScope.launch { comments.retry() }
    }

    fun onPanelDraftChange(text: String) {
        local.value = local.value.copy(panelDraft = text)
    }

    /** C3-1 输入行发送 → 一级评论（D3，插列表顶部）。 */
    fun sendPanelComment() {
        val current = local.value
        val content = current.panelDraft.trim()
        if (content.isEmpty() || current.panelSending) return

        local.value = current.copy(panelSending = true)
        viewModelScope.launch {
            val ok = comments.sendFirstLevel(content)
            val latest = local.value
            local.value = latest.copy(
                panelSending = false,
                addedCount = if (ok) latest.addedCount + 1 else latest.addedCount,
                panelDraft = if (ok) "" else latest.panelDraft,
            )
        }
    }

    /** C2-4：点「说点什么」**直接**弹遮罩输入（不经评论列表面板）。 */
    fun openCommentInput() {
        local.value = local.value.copy(
            overlay = OverlayInputState(visible = true, placeholder = "爱评论的人运气都不差"),
        )
    }

    /** C3-3：点评论「回复」→ 遮罩输入，占位预填「回复 @昵称：」。 */
    fun openReplyInput(parent: Comment, target: Comment) {
        local.value = local.value.copy(
            overlay = OverlayInputState(
                visible = true,
                placeholder = "回复 @${target.nickname}：",
                replyParent = parent,
                replyTarget = target,
            ),
        )
    }

    fun dismissOverlay() {
        local.value = local.value.copy(overlay = OverlayInputState())
    }

    /** C2-4/C3-3 发送：一级评论或回复（D3）。 */
    fun sendOverlay(text: String) {
        val current = local.value
        val content = text.trim()
        val overlay = current.overlay
        if (content.isEmpty() || overlay.sending) return

        local.value = current.copy(overlay = overlay.copy(sending = true))
        viewModelScope.launch {
            val parent = overlay.replyParent
            val target = overlay.replyTarget
            val ok = if (parent != null && target != null) {
                val done = comments.sendReply(parent, target.userId, target.nickname, content)
                if (done) toasts.show("回复成功")
                done
            } else {
                comments.sendFirstLevel(content)
            }
            val latest = local.value
            local.value = latest.copy(
                addedCount = if (ok) latest.addedCount + 1 else latest.addedCount,
                // 成功 → 收起遮罩；失败 → 遮罩保持打开并回填已输入内容（D3 内容保留）
                overlay = if (ok) OverlayInputState() else overlay.copy(sending = false, initialText = content),
            )
        }
    }
}
