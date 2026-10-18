package com.xiaohongshu.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
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
import com.xiaohongshu.app.core.ui.XhsPrimaryButton
import com.xiaohongshu.app.domain.model.Note

@Composable
internal fun ProfileScreen(
    state: ProfileUiState,
    presentation: ProfilePresentation,
    loggedIn: Boolean,
    onLeftAction: () -> Unit,
    onEditProfile: () -> Unit,
    onCopyRedId: () -> Unit,
    onTabSelect: (ProfileTab) -> Unit,
    onNoteClick: (Long, Boolean) -> Unit,
    onAuthorClick: (Long) -> Unit,
    onLikeClick: (Note) -> Unit,
    onRetry: () -> Unit,
    onLogin: () -> Unit,
    listFor: (ProfileTab) -> PagedList<Note>,
    gridFor: (ProfileTab) -> LazyStaggeredGridState,
    onFollowToggle: () -> Unit = {},
    onMessageClick: () -> Unit = {},
) {
    // 契约 §12.8：换用户或失去本人身份后，不再沿用此前访问过的私有页签。
    var visited by remember(state.user.id, state.isMe) { mutableStateOf(setOf(state.tab)) }
    LaunchedEffect(state.tab) { visited = visited + state.tab }

    val loginRequired = !loggedIn && presentation == ProfilePresentation.TAB_ROOT

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(XhsColor.Bg),
    ) {
        ProfileHeader(
            user = state.user,
            isMe = state.isMe,
            presentation = presentation,
            onLeftAction = onLeftAction,
            onCopyRedId = onCopyRedId,
            onEditProfile = if (state.isMe) onEditProfile else null,
            onBioClick = if (state.isMe) onEditProfile else null,
            onMoreClick = {},
            footer = if (state.isMe) null else {
                {
                    ProfileVisitorActions(
                        followed = state.followed,
                        onFollowToggle = onFollowToggle,
                        onMessageClick = onMessageClick,
                    )
                }
            },
        )

        ProfileSegmentRow(
            tabs = state.visibleTabs,
            selected = state.tab,
            onSelect = onTabSelect,
        )

        // 契约 §2.7 / §4 / §5：游客只能请求推入页的公开笔记，不能用 401 代替入口判断。
        if (!loginRequired) {
            visited.filter { it in state.visibleTabs && (it == ProfileTab.NOTES || loggedIn) }
                .forEach { tab ->
                    key(state.user.id, loggedIn, tab) {
                        PagedListEffectStaggered(
                            list = listFor(tab),
                            listState = gridFor(tab),
                            state = state.pageStateOf(tab),
                            autoRefreshKey = state.user.id to tab,
                        )
                    }
                }
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                loginRequired -> {
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
                !loggedIn && state.tab != ProfileTab.NOTES -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        XhsEmptyState(text = state.tab.emptyText)
                    }
                }
                else -> {
                    XhsListStateHost(
                        state = state.pageState,
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxSize().background(XhsColor.WaterfallBg),
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
}

@Composable
private fun ProfileVisitorActions(
    followed: Boolean,
    onFollowToggle: () -> Unit,
    onMessageClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.pagePadding),
        horizontalArrangement = Arrangement.spacedBy(Dimens.s8),
    ) {
        XhsFollowWideButton(
            followed = followed,
            onToggle = onFollowToggle,
            modifier = Modifier.weight(1f),
        )
        XhsMessageWideButton(
            onClick = onMessageClick,
            modifier = Modifier.weight(1f),
        )
    }
}
