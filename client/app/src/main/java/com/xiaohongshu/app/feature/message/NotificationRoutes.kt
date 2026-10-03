package com.xiaohongshu.app.feature.message

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaohongshu.app.R
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.list.PagedList
import com.xiaohongshu.app.core.list.PagedListEffect
import com.xiaohongshu.app.core.ui.XhsListFooter
import com.xiaohongshu.app.core.ui.XhsListStateHost
import com.xiaohongshu.app.core.ui.XhsOverlayInputBar
import com.xiaohongshu.app.core.ui.XhsTopBar
import com.xiaohongshu.app.data.dto.NotificationCategory
import com.xiaohongshu.app.di.LocalAppContainer
import com.xiaohongshu.app.di.appViewModel
import com.xiaohongshu.app.domain.model.NotificationItem
import com.xiaohongshu.app.navigation.AppNavigator

/**
 * G2/G3/G4 通知列表（推入式，一个路由按 [category] 区分）+ G5 四态。
 *
 * 登录态：三入口都在 G1（登录态可达）里，本页只在登录态进入；行上的写操作（G3 ♡ / 回复、
 * G4 回关）仍统一走 `LoginGate.runOrDefer`（§4.4），会话失效时由全局 I1 处理。
 * G3 回复输入用页面级遮罩（C1-5 `XhsOverlayInputBar` 同款），不再行内展开。
 *
 * **pop 时自动全标已读**（顶栏返回与系统返回共用同一路径）；G6 一键已读入口已移至 G1
 * （作用范围 = 全部三分类），本页顶栏不再自带。push 进更深页面（详情/主页）不触发。
 */
@Composable
fun NotificationListRoute(
    navigator: AppNavigator,
    category: NotificationCategory,
) {
    val container = LocalAppContainer.current
    // 一个分类一份 VM：分类是路由参数，换分类应换一份分页态
    val vm: NotificationListViewModel = appViewModel(key = "notifications-${category.value}") {
        NotificationListViewModel(
            category = category,
            repository = it.notificationRepository,
            comments = it.commentRepository,
            interactions = it.interactionStore,
            unreadCounts = it.unreadCountCenter,
            toasts = it.toastController,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val gate = container.loginGate

    // pop 时自动全标已读（两条路径共用：顶栏返回 + 系统返回/手势）。
    // 注意放在 Screen 之外：XhsOverlayInputBar 的 BackHandler 在内层、优先级更高，
    // 遮罩打开时返回先关遮罩，不会误触发「返回 + 全标已读」。
    val exit: () -> Unit = {
        vm.markAllRead()
        navigator.back()
    }
    BackHandler(onBack = exit)

    NotificationListScreen(
        state = state,
        paged = vm.paged,
        onBack = exit,
        onRowClick = { item ->
            // 顺序固定：**先标已读**（本地乐观置已读 + 角标 -1，再发请求），**再**跳 C1-1
            vm.onRowClick(item)
            if (item.postId > 0) navigator.toNoteDetail(item.postId)
        },
        onAvatarClick = { item -> navigator.toUserProfile(item.senderId) },
        // G3 的「回复」是写操作，与 ♡ / 回关一样过登录拦截（§4.4）
        onReplyClick = { item ->
            if (!gate.runOrDefer { vm.openReply(item) }) navigator.toLogin()
        },
        onReplySend = { text ->
            if (!gate.runOrDefer { vm.sendReply(text) }) navigator.toLogin()
        },
        onReplyDismiss = vm::dismissReply,
        onCommentLike = { item ->
            if (!gate.runOrDefer { vm.toggleCommentLike(item) }) navigator.toLogin()
        },
        onFollowBack = { item ->
            if (!gate.runOrDefer { vm.toggleFollowBack(item) }) navigator.toLogin()
        },
        onRetry = vm::retry,
    )
}

/** 无状态内容层：状态全部来自 [NotificationListUiState]。 */
@Composable
private fun NotificationListScreen(
    state: NotificationListUiState,
    paged: PagedList<NotificationItem>,
    onBack: () -> Unit,
    onRowClick: (NotificationItem) -> Unit,
    onAvatarClick: (NotificationItem) -> Unit,
    onReplyClick: (NotificationItem) -> Unit,
    onReplySend: (String) -> Unit,
    onReplyDismiss: () -> Unit,
    onCommentLike: (NotificationItem) -> Unit,
    onFollowBack: (NotificationItem) -> Unit,
    onRetry: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(XhsColor.Bg),
        ) {
            XhsTopBar(
                title = categoryTitle(state.category),
                onBack = onBack,
            )

            Box(modifier = Modifier.weight(1f)) {
                val listState = rememberLazyListState()
                // 每页 20 / 距底预加载 / 追加失败不清空（core/list 统一实现）
                PagedListEffect(paged, listState, state.page)

                XhsListStateHost(
                    state = state.page,
                    onRetry = onRetry,
                    // G5-1 空态文案（§4.7 定稿）
                    emptyText = "暂无消息",
                    emptyPainter = painterResource(R.drawable.ic_no_msg),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(items = state.page.items, key = { it.id }) { item ->
                            NotificationRow(
                                item = item,
                                category = state.category,
                                followed = item.senderId in state.followedSenders,
                                commentLiked = item.commentId in state.likedCommentIds,
                                onRowClick = { onRowClick(item) },
                                onAvatarClick = { onAvatarClick(item) },
                                onReplyClick = { onReplyClick(item) },
                                onCommentLike = { onCommentLike(item) },
                                onFollowBack = { onFollowBack(item) },
                            )
                        }
                        item { XhsListFooter(state = state.page) }
                    }
                }
            }
        }

        // G3 遮罩式回复输入（C1-5 同款）：点遮罩 / 系统返回 / 收起键盘取消；失败回填已输入内容
        XhsOverlayInputBar(
            visible = state.replyOverlay.isOpen,
            placeholder = state.replyOverlay.placeholder,
            initialText = state.replyOverlay.initialText,
            onSend = onReplySend,
            onDismiss = onReplyDismiss,
            sendDisabledOverride = state.replyOverlay.sending,
            dismissOnKeyboardHide = true,
        )
    }
}

/** G2/G3/G4 顶栏标题（定稿文案）。 */
private fun categoryTitle(category: NotificationCategory): String = when (category) {
    NotificationCategory.LIKE_COLLECT -> "收到的赞和收藏"
    NotificationCategory.COMMENT -> "收到的评论和@"
    NotificationCategory.FOLLOW -> "新增关注"
}
