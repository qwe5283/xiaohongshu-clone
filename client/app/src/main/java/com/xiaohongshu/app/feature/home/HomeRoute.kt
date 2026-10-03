package com.xiaohongshu.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.ui.PostWaterfall
import com.xiaohongshu.app.core.ui.XhsListFooter
import com.xiaohongshu.app.core.ui.XhsListStateHost
import com.xiaohongshu.app.di.LocalAppContainer
import com.xiaohongshu.app.di.appViewModel
import com.xiaohongshu.app.domain.model.Note
import com.xiaohongshu.app.navigation.AppNavigator

/**
 * A1/B1/B4/B5 首页：顶栏 + 双列瀑布流（关注 / 发现两页签）。
 *
 * 频道栏（B1）仅「发现」页有，作为瀑布流首项随内容滚动——向下浏览时移出视口；
 * 「关注」页不显示频道栏。
 *
 * 底 Tab 与 ＋ 按钮由 `MainScaffold` 持有，本页不渲染（A1/B1 的 tabbar 不在本组件内）；
 * 游客态的登录悬浮条则由本页渲染（A1：悬浮在瀑布流之上、底 Tab 之上）。
 */
@Composable
fun HomeRoute(navigator: AppNavigator) {
    val container = LocalAppContainer.current
    val vm: HomeViewModel = appViewModel {
        HomeViewModel(
            posts = it.postRepository,
            interactions = it.interactionStore,
            session = it.sessionManager,
            toasts = it.toastController,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val session by container.sessionManager.state.collectAsStateWithLifecycle()

    // 首次进入 / 切换页签 / 登录态变化 → 确保当前页签已加载（只加载一次，来回切换不重拉）
    LaunchedEffect(state.tab, session.loggedIn) { vm.ensureLoaded() }

    // 首页重新可见 → 静默把服务端最新笔记置顶（E5-2「列表自动刷新，新笔记置顶」）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.onResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    HomeScreen(
        state = state,
        loggedIn = session.loggedIn,
        onTabSelect = vm::selectTab,
        onChannelSelect = vm::selectChannel,
        // 左上：登录态＝点点（H1）；游客＝☰ → 登录页（A1，游客无抽屉）
        onLeftAction = { if (session.loggedIn) navigator.toAi() else navigator.toLogin() },
        onSearchClick = navigator::toSearch,
        onNoteClick = navigator::toNote,
        onAuthorClick = navigator::toUserProfile,
        // §4.3：先过登录拦截，未登录则暂存动作并推入登录页（A2，不弹 Toast）
        onLikeClick = { note ->
            if (!container.loginGate.runOrDefer { vm.toggleLike(note) }) navigator.toLogin()
        },
        onRetry = vm::retry,
        onLoadMore = vm::loadMore,
        onLogin = navigator::toLogin,
    )
}

/** 无状态内容层：状态全部来自 [HomeUiState]，方便 Preview 与单测视线。 */
@Composable
private fun HomeScreen(
    state: HomeUiState,
    loggedIn: Boolean,
    onTabSelect: (HomeTab) -> Unit,
    onChannelSelect: (String) -> Unit,
    onLeftAction: () -> Unit,
    onSearchClick: () -> Unit,
    onNoteClick: (Long, Boolean) -> Unit,
    onAuthorClick: (Long) -> Unit,
    onLikeClick: (Note) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onLogin: () -> Unit,
) {
    // 两个页签各持一个滚动状态：切换页签时两者都在组合中，滚动位置互不影响
    val discoverGrid = rememberLazyStaggeredGridState()
    val followingGrid = rememberLazyStaggeredGridState()
    val grid: LazyStaggeredGridState = when (state.tab) {
        HomeTab.DISCOVER -> discoverGrid
        HomeTab.FOLLOWING -> followingGrid
    }

    StaggeredLoadMoreEffect(
        gridState = grid,
        enabled = state.page.hasContent && !state.page.endReached,
        onLoadMore = onLoadMore,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(XhsColor.Bg),
    ) {
        HomeTopBar(
            loggedIn = loggedIn,
            tab = state.tab,
            onTabSelect = onTabSelect,
            onLeftAction = onLeftAction,
            onSearchClick = onSearchClick,
        )

        Box(modifier = Modifier.weight(1f).background(XhsColor.WaterfallBg)) {
            XhsListStateHost(
                state = state.page,
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize(),
                // B4-3 空态文案（§4.7）：首页「还没有笔记…」/ 关注流（B5）「还没有关注的人…」
                emptyText = if (state.tab == HomeTab.FOLLOWING) {
                    "还没有关注的人，去发现逛逛吧"
                } else {
                    "还没有笔记，快来发布第一条吧～"
                },
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
                        // 游客态为底部悬浮条预留空间，避免最后一行被永久遮住
                        bottom = if (loggedIn) Dimens.waterfallMargin
                        else Dimens.waterfallMargin + HomeGuestBarReserved,
                    ),
                    // 频道栏是瀑布流首项（仅发现页）：随内容滚动，向下浏览时移出视口。
                    // 白底用 drawBehind 向下多画 waterfallGap——verticalItemSpacing 是
                    // 项间统一间距、无法按项免除——以吃掉它与第一行卡片之间的缝，底缘贴合卡片；
                    // 内容同步下移 gap/2，让词条在 44dp 的白条（40 + 缝）里视觉居中
                    header = if (state.tab == HomeTab.DISCOVER) {
                        {
                            ChannelBar(
                                channels = HomeChannels,
                                selected = state.channel,
                                onSelect = onChannelSelect,
                                modifier = Modifier
                                    .bleedHorizontally(Dimens.waterfallMargin)
                                    .drawBehind {
                                        drawRect(
                                            color = XhsColor.Bg,
                                            size = Size(
                                                width = size.width,
                                                height = size.height + Dimens.waterfallGap.toPx(),
                                            ),
                                        )
                                    }
                                    .offset(y = Dimens.waterfallGap / 2),
                            )
                        }
                    } else {
                        null
                    },
                    footer = { XhsListFooter(state = state.page) },
                )
            }

            if (!loggedIn) {
                GuestLoginBar(
                    onLogin = onLogin,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = Dimens.s8, vertical = Dimens.s8),
                )
            }
        }
    }
}

/**
 * 瀑布流的「近底部」预加载（B4-4）：滚动距底约 6 项触发 `loadMore()`。
 *
 * 说明：`core/list/PagedList.kt` 的 `PagedListEffect`/`rememberNearBottom` 只适配
 * `LazyListState`，瀑布流用的是 `LazyStaggeredGridState`，故此处就近实现（已列入
 * 「需要改公共组件」建议：把它下沉为两种列表状态通用的重载）。
 */
@Composable
private fun StaggeredLoadMoreEffect(
    gridState: LazyStaggeredGridState,
    enabled: Boolean,
    onLoadMore: () -> Unit,
) {
    val nearBottom by remember(gridState) {
        derivedStateOf {
            val layout = gridState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index
                ?: return@derivedStateOf false
            lastVisible >= layout.totalItemsCount - 1 - PRELOAD_ITEM_COUNT
        }
    }

    LaunchedEffect(nearBottom, enabled) {
        if (enabled && nearBottom) onLoadMore()
    }
}

/** 距底预加载项数（B4-4：滚动距底约 600px，本项目按项数近似为 6 项）。 */
private const val PRELOAD_ITEM_COUNT = 6

/**
 * 让瀑布流 header 逃出横向 contentPadding（waterfallMargin）：测量放宽两侧 inset、
 * 整体反向平移 inset，[ChannelBar] 的右渐隐才能贴到屏幕右缘。占位尺寸仍守约束。
 */
private fun Modifier.bleedHorizontally(inset: Dp): Modifier = layout { measurable, constraints ->
    val insetPx = inset.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(maxWidth = constraints.maxWidth + insetPx * 2),
    )
    layout(constraints.maxWidth, placeable.height) { placeable.placeRelative(-insetPx, 0) }
}

/**
 * 卡片点击回传整条 Note（`core/ui/Waterfall.kt` 的 `onNoteClick: (Note) -> Unit`），
 * 故在此按 id 反查是否视频笔记，以维持 `navigator.toNote(postId, isVideo)` 的签名。
 */
private fun List<Note>.isVideoOf(postId: Long): Boolean =
    firstOrNull { it.id == postId }?.isVideo == true
