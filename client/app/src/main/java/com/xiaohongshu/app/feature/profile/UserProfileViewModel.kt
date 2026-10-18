package com.xiaohongshu.app.feature.profile

import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
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

/**
 * 契约 §12.8：推入页也可能展示本人，资料必须跟随 SessionManager，才能即时反映编辑回写。
 * 他人资料仍走契约 §1.4；请求失败时用契约 §2.12 的作者字段兜底，避免资料头全空。
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

    /**
     * 各 Tab 的瀑布流滚动状态。持有在 VM 而非 Composable 侧 `rememberLazyStaggeredGridState`
     * 的原因见 `HomeViewModel.discoverGrid` 文档（Saver 不存 lane 记忆，从本页推入笔记详情
     * 返回后上滑会跳栏；上游 issuetracker.google.com/issues/384144789）。本 VM 随本页的
     * 返回栈条目生死（key 带 userId），推入子页不销毁，返回即原位恢复。
     */
    val notesGrid = LazyStaggeredGridState()
    val collectedGrid = LazyStaggeredGridState()
    val likedGrid = LazyStaggeredGridState()

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

    /** 「赞过」Tab（契约 §4，🔒）：仅 isMe 时可达（「赞过」仅自己可见）。 */
    private val liked = PagedList<Note>(
        keyOf = { it.id },
        toasts = toasts,
        fetch = { page, size -> posts.likedPosts(userId = userId, page = page, pageSize = size) },
    )

    private val base: kotlinx.coroutines.flow.Flow<ProfileUiState> = combine(
        session.state,
        _tab,
        _author,
        _followed,
        notes.state,
    ) { sessionState, tab, author, followed, notesState ->
        val isMe = sessionState.loggedIn && sessionState.user.id == userId
        ProfileUiState(
            user = if (isMe) sessionState.user else author,
            tab = if (isMe || tab != ProfileTab.LIKED) tab else ProfileTab.NOTES,
            notesState = notesState,
            followed = followed,
            isMe = isMe,
        )
    }

    val state: StateFlow<ProfileUiState> = combine(
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
        val author = ui.user.copy(
            nickname = ui.user.nickname.ifBlank { firstNote?.authorNickname.orEmpty() },
            avatar = ui.user.avatar.ifBlank { firstNote?.authorAvatar.orEmpty() },
        )
        ui.copy(
            user = author,
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
        initialValue = ProfileUiState(
            user = if (session.isLoggedIn && session.currentUserId == userId) {
                session.state.value.user
            } else {
                User.Empty.copy(id = userId)
            },
            isMe = session.isLoggedIn && session.currentUserId == userId,
            notesState = PagedState(loading = true),
        ),
    )

    /** 契约 §6：游客不查关注态，避免 401 触发全局 I1。 */
    fun loadAuthor() {
        viewModelScope.launch {
            if (session.isLoggedIn && session.currentUserId == userId) {
                session.refreshMe()
                return@launch
            }
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
        if (tab !in state.value.visibleTabs) return
        _tab.value = tab
    }

    fun listOf(tab: ProfileTab): PagedList<Note> = when (tab) {
        ProfileTab.NOTES -> notes
        ProfileTab.COLLECTED -> collected
        ProfileTab.LIKED -> liked
    }

    /** 当前 Tab 的瀑布流滚动状态（与 [listOf] 同一套分发的 VM 持有实例）。 */
    fun gridFor(tab: ProfileTab): LazyStaggeredGridState = when (tab) {
        ProfileTab.NOTES -> notesGrid
        ProfileTab.COLLECTED -> collectedGrid
        ProfileTab.LIKED -> likedGrid
    }

    fun retry() {
        viewModelScope.launch { listOf(state.value.tab).retry() }
    }

    // ------------------------------------------------------------------ 互动

    /** D2 关注：同一状态机（`InteractionStore.toggleFollow`），基准用**服务端值**。 */
    fun toggleFollow() {
        interactions.toggleFollow(userId, _followed.value)
    }

    /** D1 点赞：入参为渲染值，先反查 raw 再交给状态机（§4.3）。 */
    fun toggleLike(id: Long) {
        val raw = notes.state.value.items.firstOrNull { it.id == id }
            ?: collected.state.value.items.firstOrNull { it.id == id }
            ?: liked.state.value.items.firstOrNull { it.id == id }
            ?: return
        interactions.toggleLike(raw)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
