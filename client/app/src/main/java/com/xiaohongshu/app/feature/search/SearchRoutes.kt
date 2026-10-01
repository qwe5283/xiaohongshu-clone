package com.xiaohongshu.app.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.design.XhsType
import com.xiaohongshu.app.core.list.PagedState
import com.xiaohongshu.app.core.list.rememberNearBottom
import com.xiaohongshu.app.core.ui.PostWaterfall
import com.xiaohongshu.app.core.ui.XhsListFooter
import com.xiaohongshu.app.core.ui.XhsListStateHost
import com.xiaohongshu.app.di.LocalAppContainer
import com.xiaohongshu.app.di.appViewModel
import com.xiaohongshu.app.domain.model.Note
import com.xiaohongshu.app.domain.model.UserBrief
import com.xiaohongshu.app.navigation.AppNavigator

/**
 * B2 搜索页（推入式独立页，无底部 Tab）。
 *
 * 结构：搜索行 52 →「历史记录」区块（**为空时整块隐藏**）→「猜你想搜」两列 →
 * 空闲区提示「输入关键词并搜索，结果将显示在这里」。
 * 提交（回车或「搜索」）→ 记录历史 → `navigator.toSearchResult(keyword)` 推入 B3-1。
 * AI「按住提问」入口并入 H（点点），本页不实现。
 */
@Composable
fun SearchRoute(navigator: AppNavigator) {
    val vm: SearchViewModel = appViewModel {
        SearchViewModel(posts = it.postRepository, history = it.searchHistoryStore)
    }
    val state by vm.state.collectAsStateWithLifecycle()

    SearchScreen(
        state = state,
        onQueryChange = vm::onQueryChange,
        onSubmit = { raw ->
            val keyword = raw.trim()
            if (keyword.isNotEmpty()) {
                vm.recordSearch(keyword)
                navigator.toSearchResult(keyword)
            }
        },
        onClearHistory = vm::clearHistory,
        onBack = navigator::back,
    )
}

/** B2 无状态内容层。 */
@Composable
private fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onClearHistory: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(XhsColor.Bg)
            .navigationBarsPadding(),
    ) {
        SearchInputRow(
            query = state.query,
            onQueryChange = onQueryChange,
            onSubmit = { onSubmit(state.query) },
            onBack = onBack,
            modifier = Modifier.statusBarsPadding(),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.pagePadding),
        ) {
            // 历史为空时内部直接 return，整块不占位
            SearchHistorySection(
                history = state.history,
                onPick = onSubmit,
                onClear = onClearHistory,
            )
            HotKeywordSection(
                keywords = state.hotKeywords,
                onPick = onSubmit,
            )
        }

        // 空闲区提示（末尾居中灰字，位于剩余空间底部）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = Dimens.pagePadding, vertical = Dimens.s16),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text(
                text = "输入关键词并搜索，结果将显示在这里",
                style = XhsType.body,
                color = XhsColor.Text3,
                maxLines = 1,
            )
        }
    }
}

/**
 * B3-1/B3-2 搜索结果页（推入式，**无底部 Tab**）。
 *
 * 搜索行保留关键词且可编辑：在本页再次提交 → **原地重查**（不新开页面）并把新关键词写入历史。
 * 搜索行下方一行筛选页签（全部/用户/视频）：全部/视频为瀑布流（视频传 `type=1`），
 * 用户为独立用户列表流（`GET /api/user/search`，仿 G4 关注条目）。空态文案见 §4.7。
 */
@Composable
fun SearchResultRoute(
    navigator: AppNavigator,
    keyword: String,
) {
    val container = LocalAppContainer.current
    val vm: SearchResultViewModel = appViewModel { appContainer ->
        SearchResultViewModel(
            posts = appContainer.postRepository,
            users = appContainer.userRepository,
            history = appContainer.searchHistoryStore,
            interactions = appContainer.interactionStore,
            session = appContainer.sessionManager,
            initialKeyword = keyword,
            toasts = appContainer.toastController,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()

    SearchResultScreen(
        state = state,
        onQueryChange = vm::onQueryChange,
        onSubmit = vm::submit,
        onFilterSelect = vm::selectFilter,
        onBack = navigator::back,
        onNoteClick = navigator::toNote,
        onAuthorClick = navigator::toUserProfile,
        onUserClick = navigator::toUserProfile,
        // §4.3：先过登录拦截，未登录则暂存动作并推入登录页（A2）
        onLikeClick = { note ->
            if (!container.loginGate.runOrDefer { vm.toggleLike(note) }) navigator.toLogin()
        },
        // 「用户」页签的关注钮同为写操作，与点赞一样过登录拦截（§4.4）
        onFollowToggle = { user ->
            if (!container.loginGate.runOrDefer { vm.toggleFollow(user) }) navigator.toLogin()
        },
        onRetry = vm::retry,
        onLoadMore = vm::loadMore,
    )
}

/** B3-1/B3-2 无状态内容层。 */
@Composable
private fun SearchResultScreen(
    state: SearchResultUiState,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onFilterSelect: (SearchResultFilter) -> Unit,
    onBack: () -> Unit,
    onNoteClick: (Long, Boolean) -> Unit,
    onAuthorClick: (Long) -> Unit,
    onUserClick: (Long) -> Unit,
    onLikeClick: (Note) -> Unit,
    onFollowToggle: (UserBrief) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    // 「全部/视频」各自一份网格滚动态：切页签不互相带走滚动位置
    val allGrid = rememberLazyStaggeredGridState()
    val videoGrid = rememberLazyStaggeredGridState()
    val grid = if (state.filter == SearchResultFilter.VIDEO) videoGrid else allGrid

    // 原地重查、或切到笔记页签时，回到列表顶部
    LaunchedEffect(state.keyword, state.filter) {
        if (state.keyword.isNotBlank() && state.showsNotes) grid.scrollToItem(0)
    }

    StaggeredLoadMoreEffect(
        gridState = grid,
        enabled = state.showsNotes && state.page.hasContent && !state.page.endReached,
        onLoadMore = onLoadMore,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(XhsColor.Bg)
            .navigationBarsPadding(),
    ) {
        SearchInputRow(
            query = state.query,
            onQueryChange = onQueryChange,
            onSubmit = onSubmit,
            onBack = onBack,
            filter = state.filter,
            onFilterSelect = onFilterSelect,
            modifier = Modifier.statusBarsPadding(),
        )

        if (state.showsNotes) {
            Box(modifier = Modifier.weight(1f).background(XhsColor.WaterfallBg)) {
                XhsListStateHost(
                    state = state.page,
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxSize(),
                    // B3-2 空态（§4.7）
                    emptyText = "暂无相关内容，换个关键词试试",
                ) {
                    PostWaterfall(
                        notes = state.notes,
                        listState = grid,
                        onNoteClick = { note -> onNoteClick(note.id, note.isVideo) },
                        onAuthorClick = onAuthorClick,
                        onLikeClick = onLikeClick,
                        contentPadding = PaddingValues(
                            start = Dimens.waterfallMargin,
                            end = Dimens.waterfallMargin,
                            top = Dimens.s0,
                            bottom = Dimens.waterfallMargin
                        ),
                        footer = { XhsListFooter(state = state.page) },
                    )
                }
            }
        } else {
            SearchUserList(
                state = state.userPage,
                followedIds = state.followedUserIds,
                selfId = state.selfId,
                onUserClick = onUserClick,
                onFollowToggle = onFollowToggle,
                onRetry = onRetry,
                onLoadMore = onLoadMore,
            )
        }
    }
}

/**
 * 「用户」页签的用户列表流：白底行式列表（非瀑布流），四态与瀑布流一致（B4-1~B4-5）。
 * 行内关注钮走 D2 状态机；命中自己（[selfId]）的行不出关注钮。
 */
@Composable
private fun SearchUserList(
    state: PagedState<UserBrief>,
    followedIds: Set<Long>,
    selfId: Long,
    onUserClick: (Long) -> Unit,
    onFollowToggle: (UserBrief) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // 距底预加载（B4-4）。这里只用「近底部」观察器而不复用 `PagedListEffect`：
    // 后者自带「挂载即刷新」，而用户页签的取数已由 VM 在切页签时发起，避免重复请求。
    val nearBottom = rememberNearBottom(listState)
    LaunchedEffect(nearBottom, state.endReached, state.items.size) {
        if (nearBottom) onLoadMore()
    }

    Box(modifier = modifier.fillMaxSize().background(XhsColor.Bg)) {
        XhsListStateHost(
            state = state,
            onRetry = onRetry,
            modifier = Modifier.fillMaxSize(),
            emptyText = "暂无相关用户，换个关键词试试",
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(items = state.items, key = { it.id }) { user ->
                    SearchUserRow(
                        user = user,
                        followed = user.id in followedIds,
                        showFollow = user.id != selfId,
                        onRowClick = { onUserClick(user.id) },
                        onFollowToggle = { onFollowToggle(user) },
                    )
                }
                item { XhsListFooter(state = state) }
            }
        }
    }
}

/**
 * 卡片点击回传整条 Note（`core/ui/Waterfall.kt` 的 `onNoteClick: (Note) -> Unit`），
 * 故在此按 id 反查是否视频笔记，以维持 `navigator.toNote(postId, isVideo)` 的签名。
 */
private fun List<Note>.isVideoOf(postId: Long): Boolean =
    firstOrNull { it.id == postId }?.isVideo == true
