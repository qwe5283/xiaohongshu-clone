package com.xiaohongshu.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohongshu.app.core.interact.InteractionStore
import com.xiaohongshu.app.core.list.PagedList
import com.xiaohongshu.app.core.list.PagedState
import com.xiaohongshu.app.core.ui.ToastController
import com.xiaohongshu.app.data.local.SessionManager
import com.xiaohongshu.app.data.repo.PostRepository
import com.xiaohongshu.app.domain.model.Note
import com.xiaohongshu.app.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** F1 我的主页 UI 状态。 */
internal data class MyProfileUiState(
    val user: User = User.Empty,
    val tab: ProfileTab = ProfileTab.NOTES,
    /** 三个 Tab 各自的分页态（交给 `XhsListStateHost` 决定四态）。 */
    val notesState: PagedState<Note> = PagedState(),
    val collectedState: PagedState<Note> = PagedState(),
    val likedState: PagedState<Note> = PagedState(),
    /** 已经过 [InteractionStore] 合并的**渲染**数据（点赞在详情页变化后返回不跳回）。 */
    val notes: List<Note> = emptyList(),
    val collected: List<Note> = emptyList(),
    val liked: List<Note> = emptyList(),
    /** 「去发布」banner 是否可见（线框 F1：关闭后本次会话不再出现）。 */
    val publishBannerVisible: Boolean = true,
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

    /** 当前 Tab 的分页态 / 展示数据。 */
    val pageState: PagedState<Note> get() = pageStateOf(tab)
    val items: List<Note> get() = itemsOf(tab)
}

/**
 * F1 我的主页（线框 F1）。
 *
 * 关键取舍：
 * - **三个 Tab 各持一个 [PagedList]**：切页签不丢对方已加载的数据；
 * - **懒加载**：请求由 UI 侧在「该 Tab 首次展示」时挂载 `PagedListEffectStaggered` 触发（见 MyProfileScreen），
 *   VM 不做首屏全量预取；因此 VM 内部不提供 `ensureLoaded`；
 * - 渲染数据一律经 `InteractionStore.mergeAll`，但点赞/收藏动作必须回传**服务端原值**
 *   （§4.3 警告：把合并值再喂回 `toggleLike` 会让计数永久偏移），故提供 [rawNote] 反查；
 * - 游客进入（正常流程由 MainScaffold 在 Tab 层拦截）：不发任何请求，避免 401 把用户推到登录页。
 */
internal class MyProfileViewModel(
    private val posts: PostRepository,
    private val session: SessionManager,
    private val interactions: InteractionStore,
    toasts: ToastController,
) : ViewModel() {

    private val _tab = MutableStateFlow(ProfileTab.NOTES)
    private val _bannerVisible = MutableStateFlow(true)

    /** F1「笔记」Tab 数据源（契约 §2.7 `GET /api/post/my`，🔒）。 */
    private val notes = PagedList<Note>(
        keyOf = { it.id },
        toasts = toasts,
        fetch = { page, size -> posts.myPosts(page = page, pageSize = size) },
    )

    /** F1「收藏」Tab（契约 §5 `GET /api/collect/posts/{me}`，🔒）。 */
    private val collected = PagedList<Note>(
        keyOf = { it.id },
        toasts = toasts,
        fetch = { page, size -> posts.collectedPosts(userId = me(), page = page, pageSize = size) },
    )

    /** F1「赞过」Tab（契约 §4 `GET /api/like/posts/{me}`，🔒）。 */
    private val liked = PagedList<Note>(
        keyOf = { it.id },
        toasts = toasts,
        fetch = { page, size -> posts.likedPosts(userId = me(), page = page, pageSize = size) },
    )

    val state: StateFlow<MyProfileUiState> = combine(
        session.state,
        _tab,
        notes.state,
        collected.state,
        liked.state,
    ) { sessionState, tab, notesState, collectedState, likedState ->
        MyProfileUiState(
            user = sessionState.user,
            tab = tab,
            notesState = notesState,
            collectedState = collectedState,
            likedState = likedState,
            notes = notesState.items,
            collected = collectedState.items,
            liked = likedState.items,
        )
    }.combine(interactions.noteOverrides) { ui, _ ->
        // 仅在渲染层合并本地乐观态（raw 值仍留在各 PagedState.items 里供写操作使用）
        ui.copy(
            notes = interactions.mergeAll(ui.notes),
            collected = interactions.mergeAll(ui.collected),
            liked = interactions.mergeAll(ui.liked),
        )
    }.combine(_bannerVisible) { ui, visible ->
        ui.copy(publishBannerVisible = visible)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        // 首帧即「加载中」，避免出现一帧空白列表（与 HomeViewModel 同款处理）
        initialValue = MyProfileUiState(notesState = PagedState(loading = true)),
    )

    init {
        // §F1：收藏/取消收藏可能在**详情页**发生，回到本页时「收藏」Tab 与小组件卡副文（collectedPostCount）
        // 都必须反映最新状态 —— 订阅 collectionVersion，仅当收藏 Tab 处于选中态时重置其分页。
        viewModelScope.launch {
            interactions.collectionVersion.drop(1).collect {
                if (!session.isLoggedIn) return@collect
                session.refreshMe()
                if (_tab.value == ProfileTab.COLLECTED) collected.refresh()
            }
        }

        // 换账号 / 退出登录后：三个列表都属于上一个用户，直接丢弃。
        // （本 VM 挂在 Tab 宿主的回退栈条目上，退出再登录不会重建，故必须显式清理。）
        viewModelScope.launch {
            session.state
                .map { it.user.id }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    notes.resetTo(emptyList())
                    collected.resetTo(emptyList())
                    liked.resetTo(emptyList())
                }
        }
    }

    // ------------------------------------------------------------------ 页签

    fun selectTab(tab: ProfileTab) {
        _tab.value = tab
    }

    /** 供 UI 挂载分页副作用用（PagedListEffectStaggered 需要具体的 PagedList 实例）。 */
    fun listOf(tab: ProfileTab): PagedList<Note> = when (tab) {
        ProfileTab.NOTES -> notes
        ProfileTab.COLLECTED -> collected
        ProfileTab.LIKED -> liked
    }

    /** B4-2 首次失败后的「重试」。 */
    fun retry() {
        viewModelScope.launch { listOf(_tab.value).retry() }
    }

    // ------------------------------------------------------------------ 互动

    /**
     * D1 点赞。入参是**渲染值**（可能已被合并过），故先按 id 反查服务端原值再交给
     * [InteractionStore.toggleLike]（§4.3：合并值不能作为 toggle 的基准）。
     */
    fun toggleLike(id: Long) {
        val raw = rawNote(id) ?: return
        viewModelScope.launch { interactions.toggleLike(raw) }
    }

    private fun rawNote(id: Long): Note? = notes.state.value.items.firstOrNull { it.id == id }
        ?: collected.state.value.items.firstOrNull { it.id == id }
        ?: liked.state.value.items.firstOrNull { it.id == id }

    // ------------------------------------------------------------------ 资料与横幅

    /** 进入 / 返回本页时刷新统计数字（关注、粉丝、获赞与收藏、小组件卡副文）。 */
    fun refreshUser() {
        if (!session.isLoggedIn) return
        viewModelScope.launch { session.refreshMe() }
    }

    /** 「去发布」banner 的关闭（线框 F1：本次会话不再出现）。 */
    fun dismissPublishBanner() {
        _bannerVisible.value = false
    }

    /** 收藏/赞过列表的 URL 需要当前用户 id；未登录时用 0（此时不会发请求）。 */
    private fun me(): Long = session.currentUserId

    private companion object {
        /** 离开页面后保留订阅 5s，避免短暂切页导致重新拉取。 */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
