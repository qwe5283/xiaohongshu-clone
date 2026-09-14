package com.xiaohongshu.app.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohongshu.app.core.interact.InteractionStore
import com.xiaohongshu.app.core.list.PagedList
import com.xiaohongshu.app.core.list.PagedState
import com.xiaohongshu.app.core.net.ApiResult
import com.xiaohongshu.app.core.ui.ToastController
import com.xiaohongshu.app.data.local.SearchHistoryStore
import com.xiaohongshu.app.data.repo.PostRepository
import com.xiaohongshu.app.domain.model.Note
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** B2 搜索页状态。 */
data class SearchUiState(
    /** 输入框内容（返回本页时保留，线框 B3-1：「← 返回 B2（保留关键词与历史）」）。 */
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
 * 历史为空时整块隐藏，🗑 清空，「猜你想搜」失败不影响页面（无线框错误态）。
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
    val page: PagedState<Note> = PagedState(),
    /** 已合并本地互动态的展示数据（§4.3）。 */
    val notes: List<Note> = emptyList(),
)

/**
 * B3-1/B3-2 搜索结果 ViewModel。
 *
 * 与 B2 的差异：本页**无底部 Tab**（推入式），且「在结果页再次提交关键词」是**原地重查**
 * （不新开页面），同时把新关键词写入搜索历史。
 */
class SearchResultViewModel(
    private val posts: PostRepository,
    private val history: SearchHistoryStore,
    initialKeyword: String,
    private val interactions: InteractionStore,
    toasts: ToastController,
) : ViewModel() {

    private val _keyword = MutableStateFlow(initialKeyword)
    private val _query = MutableStateFlow(initialKeyword)

    private val list = PagedList<Note>(
        keyOf = { it.id },
        toasts = toasts,
        fetch = { page, size ->
            posts.feed(page = page, pageSize = size, keyword = _keyword.value.ifBlank { null })
        },
    )

    val state: StateFlow<SearchResultUiState> = combine(
        _keyword,
        _query,
        list.state,
        interactions.noteOverrides,
    ) { keyword, query, page, _ ->
        SearchResultUiState(
            keyword = keyword,
            query = query,
            page = page,
            notes = interactions.mergeAll(page.items),
        )
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
            viewModelScope.launch { list.refresh() }
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    /** 结果页内再次提交：原地重查 + 写入历史（线框 B3-1）。 */
    fun submit() {
        val keyword = _query.value.trim()
        if (keyword.isEmpty()) return
        _keyword.value = keyword
        viewModelScope.launch {
            history.add(keyword)
            list.refresh()
        }
    }

    /** B4-2 首次失败后的「重试」。 */
    fun retry() {
        viewModelScope.launch { list.retry() }
    }

    /** B4-4 距底预加载下一页。 */
    fun loadMore() {
        viewModelScope.launch { list.loadMore() }
    }

    /** D1 点赞（乐观更新 + 失败回滚由 InteractionStore 负责）。 */
    fun toggleLike(note: Note) {
        viewModelScope.launch { interactions.toggleLike(note) }
    }
}

/** 离开页面后保留订阅 5s，避免转屏/短暂切页导致重新拉取。 */
private const val STOP_TIMEOUT_MS = 5_000L
