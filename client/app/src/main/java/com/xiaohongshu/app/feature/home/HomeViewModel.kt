package com.xiaohongshu.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohongshu.app.core.interact.InteractionStore
import com.xiaohongshu.app.core.list.PagedList
import com.xiaohongshu.app.core.list.PagedState
import com.xiaohongshu.app.core.net.ApiResult
import com.xiaohongshu.app.core.ui.ToastController
import com.xiaohongshu.app.data.local.SessionManager
import com.xiaohongshu.app.data.repo.PostRepository
import com.xiaohongshu.app.domain.model.Note
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** B1 顶栏中部页签。 */
enum class HomeTab { DISCOVER, FOLLOWING }

/**
 * 频道栏词条（线框 B1）。
 *
 * 线框注 #3：**点击仅高亮，不改变数据**——频道栏只是视觉态，不做服务端过滤
 * （契约 §2.5 只有 `keyword`/`type`/`sortType`，没有频道维度）。
 */
val HomeChannels: List<String> = listOf("推荐", "穿搭", "美食", "彩妆", "影视", "职场", "情感", "数码")

/** B1 首页 UI 状态（两个 Tab 各持一份分页态，互不干扰）。 */
data class HomeUiState(
    val tab: HomeTab = HomeTab.DISCOVER,
    val channel: String = HomeChannels.first(),
    val discover: PagedState<Note> = PagedState(),
    val following: PagedState<Note> = PagedState(),
    /** 已经过 [InteractionStore] 合并的展示数据（点赞在详情页变化后返回列表不跳回）。 */
    val discoverNotes: List<Note> = emptyList(),
    val followingNotes: List<Note> = emptyList(),
) {
    /** 当前 Tab 的分页态，交给 `XhsListStateHost` 决定 B4 四态。 */
    val page: PagedState<Note> get() = if (tab == HomeTab.DISCOVER) discover else following

    /** 当前 Tab 的展示数据。 */
    val notes: List<Note> get() = if (tab == HomeTab.DISCOVER) discoverNotes else followingNotes
}

/**
 * B1/B5 首页瀑布流的 ViewModel。
 *
 * 关键取舍：
 * - **两个 Tab 各自一个 [PagedList]**：切换页签不能丢对方的已加载数据；
 * - 瀑布流的滚动位置由 Compose 侧两个 `LazyStaggeredGridState` 保持（见 HomeRoute）；
 * - 渲染数据一律经 `InteractionStore.mergeAll`（§4.3：从详情页点赞返回后赞数不能跳回去）；
 * - 游客点「关注」：关注流是登录态数据（契约 §2.8 🔒），此时**不发请求**，直接给 B5 空态，
 *   避免 401 触发全局「会话过期 → 推入登录页」。
 */
class HomeViewModel(
    private val posts: PostRepository,
    private val interactions: InteractionStore,
    private val session: SessionManager,
    toasts: ToastController,
) : ViewModel() {

    private val _tab = MutableStateFlow(HomeTab.DISCOVER)
    private val _channel = MutableStateFlow(HomeChannels.first())

    private val discover = PagedList<Note>(
        keyOf = { it.id },
        toasts = toasts,
        fetch = { page, size -> posts.feed(page = page, pageSize = size) },
    )

    private val following = PagedList<Note>(
        keyOf = { it.id },
        toasts = toasts,
        fetch = { page, size -> posts.followingFeed(page = page, pageSize = size) },
    )

    private var discoverLoaded = false
    private var followingLoaded = false

    /** 已发生的「回到首页」次数；首次与首刷重合，故跳过（见 [onResumed]）。 */
    private var resumes = 0

    /** 登录态观察是否已就绪（首次发射只是初始值，不算「变化」）。 */
    private var sessionReady = false

    val state: StateFlow<HomeUiState> = combine(
        _tab,
        _channel,
        discover.state,
        following.state,
        interactions.noteOverrides,
    ) { tab, channel, discoverState, followingState, _ ->
        HomeUiState(
            tab = tab,
            channel = channel,
            discover = discoverState,
            following = followingState,
            discoverNotes = interactions.mergeAll(discoverState.items),
            followingNotes = interactions.mergeAll(followingState.items),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = HomeUiState(discover = PagedState(loading = true)),
    )

    init {
        // 登录 / 退出登录后（A6 / F6）：两个 Tab 的数据都带用户维度（following-feed 归属用户、
        // liked/collected 是用户相关字段），必须丢弃重取。放在 VM 里观察而不是在 Composable 里比较
        // 前后值——登录页是推入式页面，返回时 HomeRoute 会重新组合，Composable 侧的「上一个值」会丢。
        viewModelScope.launch {
            session.state
                .map { it.loggedIn }
                .distinctUntilChanged()
                .collect { if (sessionReady) onSessionChanged() else sessionReady = true }
        }
    }

    // ------------------------------------------------------------------ 页签 / 频道

    fun selectTab(tab: HomeTab) {
        if (_tab.value == tab) return
        _tab.value = tab
        ensureLoaded()
    }

    /** 频道点击仅高亮（线框注 #3），不发请求。 */
    fun selectChannel(channel: String) {
        _channel.value = channel
    }

    /**
     * 确保当前 Tab 的数据已加载（只加载一次，页签来回切换不重新拉取）。
     * 由 HomeRoute 在「首次进入 / 切换页签 / 登录态变化」时调用。
     */
    fun ensureLoaded() {
        when (_tab.value) {
            HomeTab.DISCOVER -> {
                if (discoverLoaded) return
                discoverLoaded = true
                viewModelScope.launch { discover.refresh() }
            }

            HomeTab.FOLLOWING -> {
                if (!session.isLoggedIn) {
                    // 游客无关注流：直接置为「已加载且为空」，渲染 B5 空态，不触发 401
                    following.resetTo(emptyList())
                    return
                }
                if (followingLoaded) return
                followingLoaded = true
                viewModelScope.launch { following.refresh() }
            }
        }
    }

    // ------------------------------------------------------------------ 分页交互

    /** B4-2 首次失败后的「重试」。 */
    fun retry() {
        val list = currentList()
        viewModelScope.launch { list.retry() }
    }

    /** B4-4 距底预加载下一页。 */
    fun loadMore() {
        val list = currentList()
        viewModelScope.launch { list.loadMore() }
    }

    // ------------------------------------------------------------------ 互动

    /**
     * D1 点赞（乐观更新 + 失败回滚由 [InteractionStore] 负责）。
     *
     * **[displayed] 是 UI 传回的合并值，这里必须先换回服务端原值**：
     * `InteractionStore.toggleXxx` 用入参的 `liked`/`likeCount` 作为基准算增量，
     * 若把已合并的值传进去，基准里已含旧增量，会被重复叠加导致计数永久偏移（见开发规范 §4.3）。
     */
    fun toggleLike(displayed: Note) {
        val raw = rawNoteOf(displayed.id) ?: displayed
        viewModelScope.launch { interactions.toggleLike(raw) }
    }

    /** 取未合并的服务端原值。合并只用于渲染，绝不回喂给 toggle。 */
    private fun rawNoteOf(id: Long): Note? =
        discover.state.value.items.firstOrNull { it.id == id }
            ?: following.state.value.items.firstOrNull { it.id == id }

    // ------------------------------------------------------------------ 回到首页时静默刷新

    /**
     * 首页重新可见时的静默刷新（E5-2「列表自动刷新，新笔记置顶」）。
     *
     * 做法：拉一次第 1 页，把**服务端最新的条目置顶**、其余已加载内容原样保留
     * （`distinctBy` 保留首次出现者，故服务端新值覆盖旧值），而不是整表 `refresh()`——
     * 后者会把已加载的多页截断成 1 页、丢掉滚动位置。
     * 因为列表是 `key` 化的（PostWaterfall 用 note.id 作 key），插入新项不会踢动视口。
     */
    fun onResumed() {
        if (resumes++ == 0) return // 首次与 ensureLoaded 的首刷重合
        val tab = _tab.value
        if (tab == HomeTab.FOLLOWING && !session.isLoggedIn) return
        viewModelScope.launch {
            when (tab) {
                HomeTab.DISCOVER -> {
                    val result = posts.feed(page = 1, pageSize = PagedList.DEFAULT_PAGE_SIZE)
                    if (result is ApiResult.Ok) {
                        discover.mutate { existing -> existing.prependFresh(result.data.items) }
                    }
                }

                HomeTab.FOLLOWING -> {
                    val result = posts.followingFeed(page = 1, pageSize = PagedList.DEFAULT_PAGE_SIZE)
                    if (result is ApiResult.Ok) {
                        following.mutate { existing -> existing.prependFresh(result.data.items) }
                    }
                }
            }
        }
    }

    private fun currentList(): PagedList<Note> =
        if (_tab.value == HomeTab.DISCOVER) discover else following

    // ------------------------------------------------------------------ 登录态变化

    /**
     * 登录成功（A6）/ 退出登录（F6）后重取数据：
     * 关注流归属上一个用户，直接丢弃；发现流的 `liked`/`collected` 也是用户相关字段，重取第一页。
     */
    private fun onSessionChanged() {
        followingLoaded = false
        following.resetTo(emptyList())
        viewModelScope.launch { discover.refresh() }
        ensureLoaded()
    }

    private companion object {
        /** 离开页面后保留订阅 5s，避免转屏/短暂切页导致重新拉取。 */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * 服务端首屏数据置顶：新笔记出现在最前，其余已加载内容顺序不变。
 * `distinctBy` 保留首次出现者 → 服务端返回的新值（含点赞数）覆盖本地旧值。
 */
private fun List<Note>.prependFresh(fresh: List<Note>): List<Note> =
    (fresh + this).distinctBy { it.id }
