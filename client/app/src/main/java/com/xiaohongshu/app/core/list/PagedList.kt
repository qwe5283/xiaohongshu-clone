package com.xiaohongshu.app.core.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.xiaohongshu.app.core.net.ApiResult
import com.xiaohongshu.app.core.net.userMessage
import com.xiaohongshu.app.core.ui.ToastController
import com.xiaohongshu.app.domain.model.Paged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 列表四态（线框 B4-1 ~ B4-5，适用于所有瀑布流列表）。
 */
data class PagedState<T>(
    val items: List<T> = emptyList(),
    /** 首次/重载加载中 → B4-1。 */
    val loading: Boolean = false,
    /** 追加下一页中 → 底部局部指示器（B4-4）。 */
    val loadingMore: Boolean = false,
    /** 首次加载失败 → 整页错误态（B4-2），值为可直接展示的文案。 */
    val error: String? = null,
    /** 是否已完成过至少一次成功加载（区分「尚未加载」与「确实为空」）。 */
    val loaded: Boolean = false,
    /** 已到列表末尾 → B4-5「没有更多了」，不再发起请求。 */
    val endReached: Boolean = false,
    val page: Int = 0,
) {
    /** B4-3 空数据。 */
    val isEmpty: Boolean get() = loaded && error == null && items.isEmpty()

    /** 有内容可渲染（含正在追加）。 */
    val hasContent: Boolean get() = items.isNotEmpty()
}

/**
 * 通用分页列表控制器。
 *
 * 设计取舍：不引入 Paging 3（需要额外依赖与 Rx/Flow 适配），而是把这个项目里
 * 每种列表都要重复一遍的分页逻辑收敛成一个小类。约定与线框一致：
 * - 每页 20 条；
 * - 滚动距底约 600px 预加载下一页（由 UI 侧触发 [loadMore]）；
 * - 到底置 [PagedState.endReached]，不再发请求；
 * - **追加失败不清空已加载内容**，只弹全局 Toast（B4-4 说明）；
 * - 失败态带「重试」（B4-2）。
 *
 * 按 [keyOf] 去重，避免新笔记插入后分页错位导致重复卡片。
 */
class PagedList<T : Any>(
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    private val keyOf: (T) -> Long = { 0L },
    private val toasts: ToastController? = null,
    private val fetch: suspend (page: Int, pageSize: Int) -> ApiResult<Paged<T>>,
) {

    private val _state = MutableStateFlow(PagedState<T>())
    val state: StateFlow<PagedState<T>> = _state.asStateFlow()

    private var inFlight = false

    /** 首次加载 / 下拉刷新（B4-1 → B4-3/B4-2）。 */
    suspend fun refresh() {
        inFlight = true
        _state.value = _state.value.copy(loading = true, error = null)
        when (val result = fetch(1, pageSize)) {
            is ApiResult.Ok -> {
                _state.value = PagedState(
                    items = result.data.items.distinctBy(keyOf),
                    loading = false,
                    loaded = true,
                    endReached = !result.data.hasMore,
                    page = 1,
                )
            }
            else -> {
                // 保留旧内容，只把错误态暴露给 UI 决策（有内容时通常静默）
                _state.value = _state.value.copy(
                    loading = false,
                    error = result.userMessage(),
                    loaded = true,
                )
            }
        }
        inFlight = false
    }

    /**
     * 追加下一页（B4-4）。到底或正在加载时为 no-op；失败弹全局 Toast 且保留内容。
     */
    suspend fun loadMore() {
        val current = _state.value
        if (inFlight || current.loading || current.loadingMore || current.endReached) return
        if (current.error != null && !current.hasContent) return
        inFlight = true
        _state.value = current.copy(loadingMore = true)
        val nextPage = current.page + 1
        when (val result = fetch(nextPage, pageSize)) {
            is ApiResult.Ok -> {
                val merged = (_state.value.items + result.data.items)
                    .distinctBy(keyOf)
                _state.value = _state.value.copy(
                    items = merged,
                    loadingMore = false,
                    loaded = true,
                    page = nextPage,
                    endReached = !result.data.hasMore,
                )
            }
            else -> {
                // 追加失败：已加载内容保留，仅全局 Toast（B4-4）
                _state.value = _state.value.copy(loadingMore = false)
                toasts?.show(result.userMessage())
            }
        }
        inFlight = false
    }

    /** 首次失败后点「重试」（B4-2）。 */
    suspend fun retry() {
        if (_state.value.hasContent) loadMore() else refresh()
    }

    // ------------------------------------------------------------ 本地变更

    /** 就地变换（乐观更新、插入新评论、替换某项）。 */
    fun mutate(transform: (List<T>) -> List<T>) {
        _state.value = _state.value.copy(items = transform(_state.value.items))
    }

    /** 头部插入并使页码语义保持正确（总页数可能变化，交由下次 refresh 校正）。 */
    fun prepend(item: T) {
        mutate { listOf(item) + it.filterNot { existing -> keyOf(existing) == keyOf(item) } }
    }

    fun removeWhere(predicate: (T) -> Boolean) {
        mutate { it.filterNot(predicate) }
    }

    /** 外部已知集合变化（如取消收藏）时，从列表剔除该项。 */
    fun removeById(id: Long) {
        mutate { it.filterNot { item -> keyOf(item) == id } }
    }

    /** 发布成功后把新笔记置顶（E5-2）。 */
    fun resetTo(items: List<T>) {
        _state.value = PagedState(
            items = items.distinctBy(keyOf),
            loading = false,
            loaded = true,
            endReached = false,
            page = 1,
        )
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 20

        /** 滚动距底约 600px 预加载下一页（线框全局规范）。 */
        const val PRELOAD_DISTANCE_PX = 600
    }
}

/**
 * 给 LazyListState 用的「近底部」观察器。
 *
 * 线框要求「滚动距底约 600px 预加载」，这里用最近可见项索引 + 阈值条数近似，
 * 避免依赖像素换算（不同屏密度下 600px 等价行数不同）。
 */
@Composable
fun rememberNearBottom(
    listState: androidx.compose.foundation.lazy.LazyListState,
    threshold: Int = 6,
): Boolean {
    val nearBottom by remember(listState) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            lastVisible >= layout.totalItemsCount - 1 - threshold
        }
    }
    return nearBottom
}

/**
 * 绑定分页加载的副作用：挂载时首刷；近底部时追加。
 * 各瀑布流页面统一用它，避免每个页面各写一遍 `LaunchedEffect`。
 */
@Composable
fun PagedListEffect(
    list: PagedList<*>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    state: PagedState<*>,
    autoRefreshKey: Any? = Unit,
) {
    val currentList = rememberUpdatedState(list)
    val currentState = rememberUpdatedState(state)

    LaunchedEffect(autoRefreshKey) {
        currentList.value.refresh()
    }

    val nearBottom = rememberNearBottom(listState)
    LaunchedEffect(nearBottom, currentState.value.endReached, currentState.value.items.size) {
        if (nearBottom) {
            currentList.value.loadMore()
        }
    }
}

/**
 * [rememberNearBottom] 的瀑布流版本。
 *
 * `LazyVerticalStaggeredGrid` 的 state 类型与 `LazyListState` 不同，两者没有公共父类型可复用，
 * 故提供这一份等价实现，让各瀑布流页面不必各写一遍（B1/B3/B5/F1/F2 共用）。
 */
@Composable
fun rememberNearBottomStaggered(
    listState: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState,
    threshold: Int = 6,
): Boolean {
    val nearBottom by remember(listState) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index
                ?: return@derivedStateOf false
            lastVisible >= layout.totalItemsCount - 1 - threshold
        }
    }
    return nearBottom
}

/**
 * 瀑布流页面的分页绑定：挂载时首刷，近底部时追加下一页。
 * 与 [PagedListEffect] 语义一致，只是 state 类型换成 [androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState]。
 */
@Composable
fun PagedListEffectStaggered(
    list: PagedList<*>,
    listState: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState,
    state: PagedState<*>,
    autoRefreshKey: Any? = Unit,
) {
    val currentList = rememberUpdatedState(list)
    val currentState = rememberUpdatedState(state)

    LaunchedEffect(autoRefreshKey) {
        currentList.value.refresh()
    }

    val nearBottom = rememberNearBottomStaggered(listState)
    LaunchedEffect(nearBottom, currentState.value.endReached, currentState.value.items.size) {
        if (nearBottom) {
            currentList.value.loadMore()
        }
    }
}
