package com.xiaohongshu.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohongshu.app.core.interact.InteractionStore
import com.xiaohongshu.app.core.list.PagedList
import com.xiaohongshu.app.core.list.PagedState
import com.xiaohongshu.app.core.net.onOk
import com.xiaohongshu.app.core.ui.ToastController
import com.xiaohongshu.app.data.local.SessionManager
import com.xiaohongshu.app.data.repo.FollowRepository
import com.xiaohongshu.app.data.repo.PostRepository
import com.xiaohongshu.app.data.repo.UserRepository
import com.xiaohongshu.app.domain.model.Note
import com.xiaohongshu.app.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** F2 他人主页 UI 状态。 */
internal data class UserProfileUiState(
    val userId: Long = 0L,
    val author: User = User.Empty,
    val tab: ProfileTab = ProfileTab.NOTES,
    val notesState: PagedState<Note> = PagedState(),
    val collectedState: PagedState<Note> = PagedState(),
    val likedState: PagedState<Note> = PagedState(),
    val notes: List<Note> = emptyList(),
    val collected: List<Note> = emptyList(),
    val liked: List<Note> = emptyList(),
    /** 已合并本地乐观态的关注态（渲染用）。 */
    val followed: Boolean = false,
    /** 自己的主页不显示关注按钮，但展示「编辑主页」pill 与「赞过」Tab（线框 D2/F1/F2）。 */
    val isMe: Boolean = false,
) {
    fun pageStateOf(tab: ProfileTab): PagedState<Note> = when (tab) {
        ProfileTab.NOTES -> notesState
        ProfileTab.COLLECTED -> collectedState
        ProfileTab.LIKED -> likedState
    }

    fun itemsOf(tab: ProfileTab): List<Note> = when (tab) {
        ProfileTab.NOTES -> notes
        ProfileTab.COLLECTED -> collected
        ProfileTab.LIKED -> liked
    }

    val pageState: PagedState<Note> get() = pageStateOf(tab)
    val items: List<Note> get() = itemsOf(tab)
}

/**
 * F2 他人主页（线框 F2）。
 *
 * 数据来源：
 * - 资料主体 `GET /api/user/{id}`（契约 §1.4，公开）→ [UserRepository]：昵称/头像/简介/背景图/
 *   性别/获赞与收藏，一次性拿全，是线框 F2 头部的权威数据源；
 * - 「笔记」`GET /api/post/user/{id}`、「收藏」`GET /api/collect/posts/{id}` → [PostRepository]；
 * - 关注数/粉丝数 `GET /api/follow/count/{id}`、关注态 `GET /api/follow/status/{id}` → [FollowRepository]。
 *
 * 兜底策略：资料请求失败时用列表返回的 `authorNickname` / `authorAvatar`（契约 §2.12）填充，
 * 保证「从卡片作者头像进来」至少能看到昵称与头像，而不是一片空白。
 */
internal class UserProfileViewModel(
    private val userId: Long,
    private val posts: PostRepository,
    private val follow: FollowRepository,
    private val users: UserRepository,
    private val session: SessionManager,
    private val interactions: InteractionStore,
    toasts: ToastController,
) : ViewModel() {

    private val _tab = MutableStateFlow(ProfileTab.NOTES)
    private val _author = MutableStateFlow(User.Empty.copy(id = userId))
    private val _followed = MutableStateFlow(false)

    /** F2「笔记」Tab（契约 §2.6，公开）。 */
    private val notes = PagedList<Note>(
        keyOf = { it.id },
        toasts = toasts,
        fetch = { page, size -> posts.userPosts(userId = userId, page = page, pageSize = size) },
    )

    /** F2「收藏」Tab（契约 §5，🔒 —— 游客不挂载分页副作用，见 UI）。 */
    private val collected = PagedList<Note>(
        keyOf = { it.id },
        toasts = toasts,
        fetch = { page, size -> posts.collectedPosts(userId = userId, page = page, pageSize = size) },
    )

    /** 「赞过」Tab（契约 §4，🔒）：仅 isMe 时可达（「赞过」仅自己可见，线框 F1 注）。 */
    private val liked = PagedList<Note>(
        keyOf = { it.id },
        toasts = toasts,
        fetch = { page, size -> posts.likedPosts(userId = userId, page = page, pageSize = size) },
    )

    private val base: kotlinx.coroutines.flow.Flow<UserProfileUiState> = combine(
        session.state,
        _tab,
        _author,
        _followed,
        notes.state,
    ) { sessionState, tab, author, followed, notesState ->
        UserProfileUiState(
            userId = userId,
            author = author,
            tab = tab,
            notesState = notesState,
            followed = followed,
            isMe = sessionState.user.id == userId,
        )
    }

    val state: StateFlow<UserProfileUiState> = combine(
        base,
        collected.state,
        liked.state,
        interactions.followOverrides,
        interactions.noteOverrides,
    ) { ui, collectedState, likedState, _, _ ->
        // 昵称/头像：资料接口不可用时用列表里的作者信息兜底（契约 §2.12 的 author* 字段）
        val firstNote = ui.notesState.items.firstOrNull()
            ?: collectedState.items.firstOrNull()
            ?: likedState.items.firstOrNull()
        val author = ui.author.copy(
            nickname = ui.author.nickname.ifBlank { firstNote?.authorNickname.orEmpty() },
            avatar = ui.author.avatar.ifBlank { firstNote?.authorAvatar.orEmpty() },
        )
        ui.copy(
            author = author,
            collectedState = collectedState,
            likedState = likedState,
            // 渲染用合并值；写操作仍按 id 反查 raw（§4.3）
            notes = interactions.mergeAll(ui.notesState.items),
            collected = interactions.mergeAll(collectedState.items),
            liked = interactions.mergeAll(likedState.items),
            followed = interactions.followedOf(userId, ui.followed),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = UserProfileUiState(
            userId = userId,
            author = User.Empty.copy(id = userId),
            isMe = session.currentUserId == userId,
            notesState = PagedState(loading = true),
        ),
    )

    /**
     * 进入页面时拉资料、关注数与关注态。
     *
     * 资料（§1.4）是头部四个区块（昵称/头像/简介/背景图/性别/获赞与收藏）的权威来源；
     * 关注数用 §6.5 覆盖，保证与关注流/粉丝数的实时性一致（资料里的计数可能是快照）。
     * 游客不发关注态请求，避免 401 触发全局 I1 推登录页。
     */
    fun loadAuthor() {
        viewModelScope.launch {
            users.getUser(userId).onOk { profile ->
                _author.value = profile.copy(id = userId)
            }
            follow.counts(userId).onOk { (following, followers) ->
                _author.value = _author.value.copy(
                    id = userId,
                    followingCount = following,
                    followersCount = followers,
                )
            }
            if (session.isLoggedIn) {
                follow.followed(userId).onOk { _followed.value = it }
            }
        }
    }

    // ------------------------------------------------------------------ 页签

    fun selectTab(tab: ProfileTab) {
        _tab.value = tab
    }

    fun listOf(tab: ProfileTab): PagedList<Note> = when (tab) {
        ProfileTab.NOTES -> notes
        ProfileTab.COLLECTED -> collected
        ProfileTab.LIKED -> liked
    }

    fun retry() {
        viewModelScope.launch { listOf(_tab.value).retry() }
    }

    // ------------------------------------------------------------------ 互动

    /** D2 关注：同一状态机（`InteractionStore.toggleFollow`），基准用**服务端值**。 */
    fun toggleFollow() {
        viewModelScope.launch { interactions.toggleFollow(userId, _followed.value) }
    }

    /** D1 点赞：入参为渲染值，先反查 raw 再交给状态机（§4.3）。 */
    fun toggleLike(id: Long) {
        val raw = notes.state.value.items.firstOrNull { it.id == id }
            ?: collected.state.value.items.firstOrNull { it.id == id }
            ?: liked.state.value.items.firstOrNull { it.id == id }
            ?: return
        viewModelScope.launch { interactions.toggleLike(raw) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
