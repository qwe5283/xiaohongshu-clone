package com.xiaohongshu.app.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import com.xiaohongshu.app.core.ui.PostWaterfall
import com.xiaohongshu.app.core.ui.XhsListFooter
import com.xiaohongshu.app.core.ui.XhsListStateHost
import com.xiaohongshu.app.di.LocalAppContainer
import com.xiaohongshu.app.di.appViewModel
import com.xiaohongshu.app.domain.model.Note
import com.xiaohongshu.app.navigation.AppNavigator

/**
 * B2 搜索页（推入式独立页，无底部 Tab）。
 *
 * 结构：搜索行 52 →「历史记录」区块（**为空时整块隐藏**）→「猜你想搜」两列 →
 * 空闲区提示「输入关键词并搜索，结果将显示在这里」。
 * 提交（回车或「搜索」）→ 记录历史 → `navigator.toSearchResult(keyword)` 推入 B3-1。
 * 线框中的 AI「按住提问」入口并入 H（点点），本页不实现。
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
            // 历史为空时内部直接 return，整块不占位（线框 B2 note）
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

        // 空闲区提示（线框 B2 末尾居中灰字，位于剩余空间底部）
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
 * 结果瀑布流与首页同款（无限滚动、四态齐全），空态文案见 §4.7。
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
            history = appContainer.searchHistoryStore,
            initialKeyword = keyword,
            interactions = appContainer.interactionStore,
            toasts = appContainer.toastController,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()

    SearchResultScreen(
        state = state,
        onQueryChange = vm::onQueryChange,
        onSubmit = vm::submit,
        onBack = navigator::back,
        onNoteClick = navigator::toNote,
        onAuthorClick = navigator::toUserProfile,
        // §4.3：先过登录拦截，未登录则暂存动作并推入登录页（A2）
        onLikeClick = { note ->
            if (!container.loginGate.runOrDefer { vm.toggleLike(note) }) navigator.toLogin()
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
    onBack: () -> Unit,
    onNoteClick: (Long, Boolean) -> Unit,
    onAuthorClick: (Long) -> Unit,
    onLikeClick: (Note) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val grid = rememberLazyStaggeredGridState()

    // 原地重查后回到列表顶部
    LaunchedEffect(state.keyword) {
        if (state.keyword.isNotBlank()) grid.scrollToItem(0)
    }

    StaggeredLoadMoreEffect(
        gridState = grid,
        enabled = state.page.hasContent && !state.page.endReached,
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
            modifier = Modifier.statusBarsPadding(),
        )

        Box(modifier = Modifier.weight(1f)) {
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
                    footer = { XhsListFooter(state = state.page) },
                )
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
