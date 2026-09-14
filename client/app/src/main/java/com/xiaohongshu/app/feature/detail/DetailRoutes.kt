package com.xiaohongshu.app.feature.detail

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaohongshu.app.core.design.XhsTheme
import com.xiaohongshu.app.di.LocalAppContainer
import com.xiaohongshu.app.di.appViewModel
import com.xiaohongshu.app.navigation.AppNavigator
import kotlinx.coroutines.launch

/*
 * C1/C2 详情入口（由 AppNavHost 注册）。
 *
 * 入口签名固定：`NoteDetailRoute(navigator, postId)` / `VideoDetailRoute(navigator, postId)`。
 * 登录拦截（A2，§4.4）统一在**本层**做：写操作先 `loginGate.runOrDefer`，返回 false 时推入登录页，
 * 且**不弹 Toast**（线框明确）；登录成功后 `popLogin()` 会补执行被暂存的动作。
 */

/** C1-1 ~ C1-5 图文详情（浅色主题）+ D1/D3。 */
@Composable
fun NoteDetailRoute(
    navigator: AppNavigator,
    postId: Long,
) {
    val container = LocalAppContainer.current
    val vm: NoteDetailViewModel = appViewModel {
        NoteDetailViewModel(
            postId = postId,
            postRepository = it.postRepository,
            commentRepository = it.commentRepository,
            followRepository = it.followRepository,
            interactionStore = it.interactionStore,
            sessionManager = it.sessionManager,
            toasts = it.toastController,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val session by container.sessionManager.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val gate = container.loginGate

    NoteDetailScreen(
        state = state,
        myAvatar = session.user.avatar,
        listState = listState,
        onBack = navigator::back,
        onRetry = vm::retry,
        onAuthorClick = navigator::toUserProfile,
        // D2 关注：详情作者栏用描边胶囊（XhsFollowPill）
        onFollowClick = { if (!gate.runOrDefer { vm.toggleFollow() }) navigator.toLogin() },
        // D1 点赞 / 收藏：一律经 InteractionStore（乐观 + 回滚 + 跨页一致）
        onLikeClick = { if (!gate.runOrDefer { vm.toggleLike() }) navigator.toLogin() },
        onCollectClick = { if (!gate.runOrDefer { vm.toggleCollect() }) navigator.toLogin() },
        onCommentCountClick = {
            // 线框未定义底栏 💬 的行为：滚动到评论区（页面内嵌流）
            scope.launch { listState.animateScrollToItem(COMMENT_HEADER_INDEX) }
        },
        onCommentLike = { id -> if (!gate.runOrDefer { vm.toggleCommentLike(id) }) navigator.toLogin() },
        // C1-5：点「回复」只改底部输入栏（页面流内，不弹遮罩面板）
        onReplyClick = vm::toggleReply,
        onExpandGroup = vm::expandReplies,
        // D3：点输入胶囊聚焦底部输入栏（游客先拦截）
        onComposeStart = { if (!gate.runOrDefer { vm.startComposing() }) navigator.toLogin() },
        onCancelCompose = vm::cancelComposing,
        onDraftChange = vm::onDraftChange,
        onSend = vm::send,
        onLoadMore = vm::loadMoreComments,
        onRetryComments = vm::retryComments,
    )
}

/** C2-1 ~ C2-5 视频详情（深色沉浸）+ C3-1 ~ C3-5 评论面板。 */
@Composable
fun VideoDetailRoute(
    navigator: AppNavigator,
    postId: Long,
) {
    val container = LocalAppContainer.current
    val vm: VideoDetailViewModel = appViewModel {
        VideoDetailViewModel(
            postId = postId,
            postRepository = it.postRepository,
            commentRepository = it.commentRepository,
            followRepository = it.followRepository,
            interactionStore = it.interactionStore,
            sessionManager = it.sessionManager,
            toasts = it.toastController,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val session by container.sessionManager.state.collectAsStateWithLifecycle()
    val gate = container.loginGate

    // 深色沉浸：本页用 LocalXhsDark 切换中性色（分隔线/骨架/胶囊底），不改变全局主题
    XhsTheme(dark = true) {
        VideoDetailScreen(
            state = state,
            myAvatar = session.user.avatar,
            onBack = navigator::back,
            onSearch = navigator::toSearch,
            onRetry = vm::retry,
            onAuthorClick = navigator::toUserProfile,
            onFollowClick = { if (!gate.runOrDefer { vm.toggleFollow() }) navigator.toLogin() },
            onLikeClick = { if (!gate.runOrDefer { vm.toggleLike() }) navigator.toLogin() },
            onCollectClick = { if (!gate.runOrDefer { vm.toggleCollect() }) navigator.toLogin() },
            // C2-4：点「说点什么」直接弹遮罩输入（游客先拦截）
            onOpenCommentInput = { if (!gate.runOrDefer { vm.openCommentInput() }) navigator.toLogin() },
            // C3-1：点「💬」打开评论面板（只读浏览，不需要登录）
            onOpenComments = vm::openComments,
            onCloseComments = vm::closeComments,
            onOverlayDismiss = vm::dismissOverlay,
            onOverlaySend = vm::sendOverlay,
            onPanelDraftChange = vm::onPanelDraftChange,
            onPanelSend = vm::sendPanelComment,
            onCommentLike = { id -> if (!gate.runOrDefer { vm.toggleCommentLike(id) }) navigator.toLogin() },
            // C3-3：面板里点「回复」→ 遮罩式回复输入
            onReplyClick = { parent, target ->
                if (!gate.runOrDefer { vm.openReplyInput(parent, target) }) navigator.toLogin()
            },
            onExpandGroup = vm::expandReplies,
            onLoadMore = vm::loadMoreComments,
            onRetryComments = vm::retryComments,
        )
    }
}
