package com.xiaohongshu.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.list.PagedList
import com.xiaohongshu.app.core.list.PagedListEffectStaggered
import com.xiaohongshu.app.core.ui.PostWaterfall
import com.xiaohongshu.app.core.ui.XhsEmptyState
import com.xiaohongshu.app.core.ui.XhsFollowWideButton
import com.xiaohongshu.app.core.ui.XhsListFooter
import com.xiaohongshu.app.core.ui.XhsListStateHost
import com.xiaohongshu.app.domain.model.Note

/**
 * F2 他人主页内容层（无状态；路由与 VM 见 [com.xiaohongshu.app.feature.profile.UserProfileRoute]）。
 *
 * isMe（评论区点自己头像进入）时是「推入态的个人页」：顶栏 ← + 「编辑主页」pill、
 * 隐藏关注按钮、Tab 含「赞过」——与原版仅差左上角按钮与底 Tab（底 Tab 由宿主决定，本页无感）。
 */
@Composable
internal fun UserProfileScreen(
    state: UserProfileUiState,
    loggedIn: Boolean,
    onBack: () -> Unit,
    onMoreClick: () -> Unit,
    onEditProfile: () -> Unit,
    onCopyRedId: () -> Unit,
    onFollowToggle: () -> Unit,
    onTabSelect: (ProfileTab) -> Unit,
    onNoteClick: (Long, Boolean) -> Unit,
    onAuthorClick: (Long) -> Unit,
    onLikeClick: (Note) -> Unit,
    onRetry: () -> Unit,
    listFor: (ProfileTab) -> PagedList<Note>,
) {
    val notesGrid = rememberLazyStaggeredGridState()
    val collectedGrid = rememberLazyStaggeredGridState()
    val likedGrid = rememberLazyStaggeredGridState()
    fun gridFor(tab: ProfileTab): LazyStaggeredGridState = when (tab) {
        ProfileTab.NOTES -> notesGrid
        ProfileTab.COLLECTED -> collectedGrid
        ProfileTab.LIKED -> likedGrid
    }

    // 懒加载：只有展示过的 Tab 才挂载分页副作用（挂载即首刷一次，之后常驻）
    var visited by remember { mutableStateOf(setOf(state.tab)) }
    LaunchedEffect(state.tab) { if (state.tab !in visited) visited = visited + state.tab }

    // 契约 §5：`GET /api/collect/posts/{userId}` 需要登录态 —— 游客不发请求，
    // 否则 401 会触发 I1「清态 + 推入登录页」的全局流程。
    val collectedLocked = !loggedIn && state.tab == ProfileTab.COLLECTED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(XhsColor.Bg),
    ) {
        ProfileHeader(
            user = state.author,
            isMe = state.isMe,
            pushed = true,
            onLeftAction = onBack,
            onCopyRedId = onCopyRedId,
            onMoreClick = onMoreClick,
            // isMe 槽位：编辑 pill 与简介引导（点简介也进编辑，与 F1 行为一致）
            onEditProfile = if (state.isMe) onEditProfile else null,
            onBioClick = if (state.isMe) onEditProfile else null,
            footer = {
                // D2：他人主页用通栏大按钮；自己的主页不显示（线框 F2/D2）
                if (!state.isMe) {
                    XhsFollowWideButton(
                        followed = state.followed,
                        onToggle = onFollowToggle,
                        modifier = Modifier.padding(horizontal = Dimens.pagePadding),
                    )
                }
            },
        )

        // 「赞过」仅自己可见（线框 F1 注）：isMe 时用全量 Tab，否则公集
        ProfileSegmentRow(
            tabs = if (state.isMe) ProfileTab.entries else ProfileTab.Public,
            selected = state.tab,
            onSelect = onTabSelect,
        )

        Box(modifier = Modifier.weight(1f)) {
            if (collectedLocked) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    XhsEmptyState(text = ProfileTab.COLLECTED.emptyText)
                }
            } else {
                visited.forEach { tab ->
                    if (tab == ProfileTab.NOTES || loggedIn) {
                        key(tab) {
                            PagedListEffectStaggered(
                                list = listFor(tab),
                                listState = gridFor(tab),
                                state = state.pageStateOf(tab),
                                autoRefreshKey = tab,
                            )
                        }
                    }
                }

                XhsListStateHost(
                    state = state.pageState,
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxSize(),
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
            }
        }
    }
}
