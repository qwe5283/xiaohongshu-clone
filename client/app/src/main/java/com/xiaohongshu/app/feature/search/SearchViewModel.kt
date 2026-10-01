package com.xiaohongshu.app.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohongshu.app.core.interact.InteractionStore
import com.xiaohongshu.app.core.list.PagedList
import com.xiaohongshu.app.core.list.PagedState
import com.xiaohongshu.app.core.net.ApiResult
import com.xiaohongshu.app.core.ui.ToastController
import com.xiaohongshu.app.data.local.SearchHistoryStore
import com.xiaohongshu.app.data.local.SessionManager
import com.xiaohongshu.app.data.repo.PostRepository
import com.xiaohongshu.app.data.repo.UserRepository
import com.xiaohongshu.app.domain.model.Note
import com.xiaohongshu.app.domain.model.UserBrief
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** B2 搜索页状态。 */
data class SearchUiState(
    /** 输入框内容（返回本页时保留关键词与历史）。 */
    val query: String = "",
    /** 本地搜索历史，最新在前；为空时「历史记录」整块隐藏。 */
    val history: List<String> = emptyList(),
    /** 「猜你想搜」两列词条（契约 §2.9）。 */
    val hotKeywords: List<String> = emptyList(),
)

/**
 * B2 搜索页 ViewModel。
 *
 * 搜索历史是纯本地能力（契约 §11）：点「搜索」或回车 → 记录关键词 → 推入 B3-1。
 * 历史为空时整块隐藏，🗑 清空，「猜你想搜」失败不影响页面（无错误态）。
 */
class SearchViewModel(
    private val posts: PostRepository,
    private val history: SearchHistoryStore,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _history = MutableStateFlow<List<String>>(emptyList())
    private val _hotKeywords = MutableStateFlow<List<String>>(emptyList())

    val state: StateFlow<SearchUiState> = combine(_query, _history, _hotKeywords) { query, history, hot ->
        SearchUiState(query = query, history = history, hotKeywords = hot)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SearchUiState(),
    )

    init {
        viewModelScope.launch {
            history.history.collect { _history.value = it }
        }
        viewModelScope.launch {
            when (val result = posts.hotKeywords()) {
                is ApiResult.Ok -> _hotKeywords.value = result.data
                // 猜你想搜失败不阻塞搜索页：保持该区块为空即可
                else -> Unit
            }
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    /** 记录一次搜索（提交时调用；`add` 内部已去重并置于最前）。 */
    fun recordSearch(keyword: String) {
        viewModelScope.launch { history.add(keyword) }
    }

    /** 🗑 清空历史。 */
    fun clearHistory() {
        viewModelScope.launch { history.clear() }
    }
}

/** B3-1/B3-2 搜索结果页状态。 */
data class SearchResultUiState(
    /** 当前生效的关键词（提交后原地变化）。 */
    val keyword: String = "",
    /** 输入框内容，可继续编辑。 */
    val query: String = "",
    /** 当前选中页签：全部 / 用户 / 视频。 */
    val filter: SearchResultFilter = SearchResultFilter.ALL,
    /** 「全部/视频」页签的笔记分页态（在选中页签的列表上取）。 */
    val page: PagedState<Note> = PagedState(),
    /** 已合并本地互动态的展示数据（§4.3）。 */
    val notes: List<Note> = emptyList(),
    /** 「用户」页签的分页态。 */
    val userPage: PagedState<UserBrief> = PagedState(),
    /** 「用户」页签里「已关注」（合并本地乐观覆盖）的用户 id。 */
    val followedUserIds: Set<Long> = emptySet(),
    /** 自己的 id：命中的是自己行时不出关注钮（与详情/他人主页同规则）；未登录为 0。 */
    val selfId: Long = 0,
) {
    /** 当前页签是否为瀑布流（「全部/视频」）。 */
    val showsNotes: Boolean get() = filter != SearchResultFilter.USER
}

/**
 * B3-1/B3-2 搜索结果 ViewModel。
 *
 * 与 B2 的差异：本页**无底部 Tab**（推入式），且「在结果页再次提交关键词」是**原地重查**
 * （不新开页面），同时把新关键词写入搜索历史。
 *
 * 三个筛选页签（契约 §1.8 / §2.5）各持一份分页态：
 * - 「全部」`type` 缺省、「视频」传 `type=1`（变更 #18），都走 `GET /api/post/list`；
 * - 「用户」走 `GET /api/user/search`，是独立的用户列表流（仿 G4 关注条目）。
 * 切页签只补拉「当前关键词下尚未加载」的那一份；再次提交关键词则三份数据同时作废、
 * 只重查当前页签（其余在切过去时补拉），避免一次提交打三个请求。
 */
class SearchResultViewModel(
    private val posts: PostRepository,
    private val users: UserRepository,
    private val history: SearchHistoryStore,
    private val interactions: InteractionStore,
    private val session: SessionManager,
    initialKeyword: String,
    toasts: ToastController,
) : ViewModel() {

    private val _keyword = MutableStateFlow(initialKeyword)
    private val _query = MutableStateFlow(initialKeyword)
    private val _filter = MutableStateFlow(SearchResultFilter.ALL)

    private val allNotes = PagedList<Note>(
        keyOf = { it.id },
        toasts = toasts,
        fetch = { page, size ->
            posts.feed(page = page, pageSize = size, keyword = keywordOrNull(), type = null)
        },
    )

    private val videoNotes = PagedList<Note>(
        keyOf = { it.id },
        toasts = toasts,
        fetch = { page, size ->
            posts.feed(page = page, pageSize = size, keyword = keywordOrNull(), type = PostRepository.POST_TYPE_VIDEO)
        },
    )

    private val userRows = PagedList<UserBrief>(
        keyOf = { it.id },
        toasts = toasts,
        fetch = { page, size -> users.searchUsers(_keyword.value, page, size) },
    )

    /** 各页签已加载数据所属的关键词；与当前关键词不一致 = 切过去时需要补拉。 */
    private val loadedKeyword = mutableMapOf<SearchResultFilter, String>()

    val state: StateFlow<SearchResultUiState> = run {
        val meta = combine(_keyword, _query, _filter) { keyword, query, filter -> Triple(keyword, query, filter) }
        val tabs = combine(allNotes.state, videoNotes.state, userRows.state) { all, video, userState ->
            Triple(all, video, userState)
        }
        combine(meta, tabs, interactions.followOverrides, session.state) { (keyword, query, filter), (all, video, userPage), follows, sessionState ->
            val notesPage = if (filter == SearchResultFilter.VIDEO) video else all
            SearchResultUiState(
                keyword = keyword,
                query = query,
                filter = filter,
                page = notesPage,
                notes = interactions.mergeAll(notesPage.items),
                userPage = userPage,
                followedUserIds = userPage.items
                    .filter { interactions.followedOf(it.id, it.followed) }
                    .map { it.id }
                    .toSet(),
                selfId = if (sessionState.loggedIn) sessionState.user.id else 0L,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SearchResultUiState(
            keyword = initialKeyword,
            query = initialKeyword,
            page = PagedState(loading = true),
        ),
    )

    init {
        // B2 提交时已写入历史，这里只负责取数
        if (initialKeyword.isNotBlank()) {
            viewModelScope.launch { load(SearchResultFilter.ALL) }
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    /** 结果页内再次提交：原地重查 + 写入历史。 */
    fun submit() {
        val keyword = _query.value.trim()
        if (keyword.isEmpty()) return
        _keyword.value = keyword
        // 关键词已变：三个页签的既有数据全部作废，只重查当前页签
        loadedKeyword.clear()
        viewModelScope.launch {
            history.add(keyword)
            load(_filter.value)
        }
    }

    /** 切换筛选页签；该页签尚未按当前关键词取过数时补拉。 */
    fun selectFilter(filter: SearchResultFilter) {
        if (_filter.value == filter) return
        _filter.value = filter
        if (_keyword.value.isNotBlank() && loadedKeyword[filter] != _keyword.value) {
            viewModelScope.launch { load(filter) }
        }
    }

    /** B4-2 首次失败后的「重试」（当前页签）。 */
    fun retry() {
        viewModelScope.launch { paged(_filter.value).retry() }
    }

    /** B4-4 距底预加载下一页（当前页签）。 */
    fun loadMore() {
        viewModelScope.launch { paged(_filter.value).loadMore() }
    }

    /** D1 点赞（乐观更新 + 失败回滚由 InteractionStore 负责）。 */
    fun toggleLike(note: Note) {
        viewModelScope.launch { interactions.toggleLike(note) }
    }

    /**
     * 「用户」页签的关注/取关（D2 同一状态机）。
     * 服务端基准值传该行的 `followed`（列表项服务端原值），展示态由 [SearchResultUiState.followedUserIds] 合并。
     */
    fun toggleFollow(user: UserBrief) {
        viewModelScope.launch { interactions.toggleFollow(user.id, user.followed) }
    }

    // ------------------------------------------------------------------ 内部

    /** 取当前关键词下的数据（写 `loadedKeyword` 后再拉，翻页/刷新共用入口）。 */
    private suspend fun load(filter: SearchResultFilter) {
        loadedKeyword[filter] = _keyword.value
        paged(filter).refresh()
    }

    private fun paged(filter: SearchResultFilter): PagedList<*> = when (filter) {
        SearchResultFilter.ALL -> allNotes
        SearchResultFilter.VIDEO -> videoNotes
        SearchResultFilter.USER -> userRows
    }

    private fun keywordOrNull(): String? = _keyword.value.ifBlank { null }
}

/** 离开页面后保留订阅 5s，避免转屏/短暂切页导致重新拉取。 */
private const val STOP_TIMEOUT_MS = 5_000L
