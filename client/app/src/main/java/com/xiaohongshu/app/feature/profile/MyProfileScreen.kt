package com.xiaohongshu.app.feature.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.design.XhsType
import com.xiaohongshu.app.core.list.PagedList
import com.xiaohongshu.app.core.list.PagedListEffectStaggered
import com.xiaohongshu.app.core.ui.PlaceholderIconRes
import com.xiaohongshu.app.core.ui.PostWaterfall
import com.xiaohongshu.app.core.ui.XhsDivider
import com.xiaohongshu.app.core.ui.XhsEmptyState
import com.xiaohongshu.app.core.ui.XhsListFooter
import com.xiaohongshu.app.core.ui.XhsListStateHost
import com.xiaohongshu.app.core.ui.XhsPrimaryButton
import com.xiaohongshu.app.core.ui.XhsScrim
import com.xiaohongshu.app.domain.model.Note
import com.xiaohongshu.app.domain.model.User

/** F1 我的主页内容层（无状态；路由与 VM 见 [com.xiaohongshu.app.feature.profile.MyProfileRoute]）。 */
@Composable
internal fun MyProfileScreen(
    state: MyProfileUiState,
    loggedIn: Boolean,
    onLeftAction: () -> Unit,
    onEditProfile: () -> Unit,
    onCopyRedId: () -> Unit,
    onBioClick: () -> Unit,
    onTabSelect: (ProfileTab) -> Unit,
    onNoteClick: (Long, Boolean) -> Unit,
    onAuthorClick: (Long) -> Unit,
    onLikeClick: (Note) -> Unit,
    onRetry: () -> Unit,
    onLogin: () -> Unit,
    listFor: (ProfileTab) -> PagedList<Note>,
) {
    // 三个 Tab 各持一个滚动状态：切页签互不影响
    val notesGrid = rememberLazyStaggeredGridState()
    val collectedGrid = rememberLazyStaggeredGridState()
    val likedGrid = rememberLazyStaggeredGridState()
    fun gridFor(tab: ProfileTab): LazyStaggeredGridState = when (tab) {
        ProfileTab.NOTES -> notesGrid
        ProfileTab.COLLECTED -> collectedGrid
        ProfileTab.LIKED -> likedGrid
    }

    // 懒加载：只有「已展示过」的 Tab 才挂载分页副作用 —— 挂载即首刷一次，
    // 之后随页面常驻，页签来回切换不会重复请求（线框 F1「三 Tab 懒加载」）。
    // 以 user.id 为 key：换账号后已展示集合与分页副作用一起重置，不会沿用上一个用户的数据。
    var visited by remember(state.user.id) { mutableStateOf(setOf(state.tab)) }
    LaunchedEffect(state.tab) { if (state.tab !in visited) visited = visited + state.tab }

    Column(modifier = Modifier.fillMaxSize()) {
        ProfileHeader(
            user = state.user,
            isMe = true,
            onLeftAction = onLeftAction,
            onCopyRedId = onCopyRedId,
            onEditProfile = onEditProfile,
            onBioClick = onBioClick,
        )

        Spacer(modifier = Modifier.height(Dimens.s12))

        ProfileSegmentRow(
            tabs = ProfileTab.entries,
            selected = state.tab,
            onSelect = onTabSelect,
        )

        Box(modifier = Modifier.weight(1f)) {
            if (loggedIn) {
                // 已展示过的 Tab 的分页副作用（懒加载 + 近底部追加，B4-1~B4-5）
                visited.forEach { tab ->
                    key(tab) {
                        PagedListEffectStaggered(
                            list = listFor(tab),
                            listState = gridFor(tab),
                            state = state.pageStateOf(tab),
                            // 换账号后同一个 Tab 也要重新拉取（key 变化 → 副作用重跑）
                            autoRefreshKey = tab to state.user.id,
                        )
                    }
                }

                XhsListStateHost(
                    state = state.pageState,
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxSize(),
                    // B4-3 空态文案（§4.7 定稿：暂无笔记 / 暂无收藏 / 暂无点赞）
                    emptyText = state.tab.emptyText,
                ) {
                    PostWaterfall(
                        notes = state.items,
                        listState = gridFor(state.tab),
                        onNoteClick = { note -> onNoteClick(note.id, note.isVideo) },
                        onAuthorClick = onAuthorClick,
                        onLikeClick = onLikeClick,
                        footer = { XhsListFooter(state = state.pageState) },
                    )
                }
            } else {
                // 游客兜底：正常流程由 MainScaffold 在 Tab 层拦截（A1/A2），到不了这里；
                // 一旦到达也不发请求（三个 Tab 均为 🔒 接口），只给登录入口。
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    XhsEmptyState(
                        text = "登录后体验更多功能",
                        action = {
                            XhsPrimaryButton(
                                text = "登录",
                                onClick = onLogin,
                                fillWidth = false,
                                modifier = Modifier.padding(horizontal = Dimens.s32),
                            )
                        },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- F4 抽屉

/**
 * F4 抽屉（线框：宽 308、项 284×52 @12（icon 24 @28、文字 @64）、分组间隔 8、底部三宫格 94.6×72）。
 *
 * 「设置」→ F5；「社区公约」「关于我们」为**视觉占位**（线框 #9 未定义行为，故点击不做任何事）；
 * 「退出登录」→ F6。点遮罩关闭。仅登录态可达（由调用方保证）。
 */
@Composable
internal fun ProfileDrawer(
    visible: Boolean,
    user: User,
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
        ) {
            XhsScrim(onClick = onDismiss)
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(tween(220)) { -it },
            exit = slideOutHorizontally(tween(180)) { -it },
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Column(
                modifier = Modifier
                    .width(Dimens.drawerWidth)
                    .fillMaxHeight()
                    .background(XhsColor.Bg),
            ) {
                Spacer(modifier = Modifier.statusBarsPadding())

                Text(
                    text = user.displayName,
                    style = XhsType.pageTitle,
                    color = XhsColor.Text1,
                    modifier = Modifier.padding(
                        start = Dimens.pagePadding,
                        end = Dimens.pagePadding,
                        top = Dimens.s16,
                        bottom = Dimens.s16,
                    ),
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.s12),
                ) {
                    DrawerItem(label = "设置", onClick = onSettings)
                    XhsDivider()
                    DrawerItem(label = "社区公约", onClick = {})
                    XhsDivider()
                    DrawerItem(label = "关于我们", onClick = {})
                }

                Spacer(modifier = Modifier.height(Dimens.drawerGroupGap))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.s12),
                ) {
                    DrawerItem(label = "退出登录", onClick = onLogout, emphasized = true)
                }

                Spacer(modifier = Modifier.weight(1f))

                // 底部三宫格（线框 T-3：94.6×72；内容线框未定义 → 仅按尺寸 + 占位素材标记待定）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.s12)
                        .height(Dimens.drawerGridCell),
                ) {
                    repeat(DRAWER_GRID_CELLS) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(Dimens.drawerGridCell),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(PlaceholderIconRes),
                                contentDescription = null,
                                tint = XhsColor.Text3,
                                modifier = Modifier.size(Dimens.icon24),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }
    }
}

/** 抽屉底部三宫格格数（线框 T-3）。 */
private const val DRAWER_GRID_CELLS = 3

/**
 * 抽屉项：高 52、icon 24 @28、文字 @64（线框 T-3）。
 * 图标在 Dimens 里没有对应素材 → 统一用 [PlaceholderIconRes]（§4.5）。
 */
@Composable
private fun DrawerItem(
    label: String,
    onClick: () -> Unit,
    emphasized: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.rowDrawer)
            .clickable(onClick = onClick)
            // 分组左边距 12 + 项内 16 = icon @28（实测）
            .padding(start = Dimens.s16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(PlaceholderIconRes),
            contentDescription = null,
            tint = XhsColor.Text2,
            modifier = Modifier.size(Dimens.icon24),
        )
        Spacer(modifier = Modifier.width(Dimens.s12))
        Text(
            text = label,
            style = XhsType.s(15, emphasis = emphasized),
            color = XhsColor.Text1,
            maxLines = 1,
        )
    }
}
