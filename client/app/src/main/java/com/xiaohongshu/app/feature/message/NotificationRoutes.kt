package com.xiaohongshu.app.feature.message

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaohongshu.app.R
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.list.PagedList
import com.xiaohongshu.app.core.list.PagedListEffect
import com.xiaohongshu.app.core.ui.XhsConfirmSheet
import com.xiaohongshu.app.core.ui.XhsIconButton
import com.xiaohongshu.app.core.ui.XhsListFooter
import com.xiaohongshu.app.core.ui.XhsListStateHost
import com.xiaohongshu.app.core.ui.XhsTopBar
import com.xiaohongshu.app.data.dto.NotificationCategory
import com.xiaohongshu.app.di.LocalAppContainer
import com.xiaohongshu.app.di.appViewModel
import com.xiaohongshu.app.domain.model.NotificationItem
import com.xiaohongshu.app.navigation.AppNavigator

/**
 * G2/G3/G4 通知列表（推入式，一个路由按 [category] 区分）+ G5 四态 + G6 一键已读。
 *
 * 登录态：三入口都在 G1（登录态可达）里，本页只在登录态进入；行内的写操作（G3 ♡ / 回复、
 * G4 回关）仍统一走 `LoginGate.runOrDefer`（§4.4），会话失效时由全局 I1 处理。
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

    NotificationListScreen(
        state = state,
        paged = vm.paged,
        onBack = navigator::back,
        onRowClick = { item ->
            // 顺序固定：**先标已读**（本地乐观置已读 + 角标 -1，再发请求），**再**跳 C1-1
            vm.onRowClick(item)
            if (item.postId > 0) navigator.toNoteDetail(item.postId)
        },
        onAvatarClick = { item -> navigator.toUserProfile(item.senderId) },
        // G3 的「回复」是写操作，与 ♡ / 回关一样过登录拦截（§4.4）
        onReplyToggle = { item ->
            val toggle = {
                if (state.reply.targetId == item.id) vm.closeReply() else vm.openReply(item)
            }
            if (!gate.runOrDefer(toggle)) navigator.toLogin()
        },
        onReplyChange = vm::onReplyChange,
        onReplySend = { item ->
            if (!gate.runOrDefer { vm.sendReply(item) }) navigator.toLogin()
        },
        onCommentLike = { item ->
            if (!gate.runOrDefer { vm.toggleCommentLike(item) }) navigator.toLogin()
        },
        onFollowBack = { item ->
            if (!gate.runOrDefer { vm.toggleFollowBack(item) }) navigator.toLogin()
        },
        onMarkAllRead = vm::markAllRead,
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
    onReplyToggle: (NotificationItem) -> Unit,
    onReplyChange: (String) -> Unit,
    onReplySend: (NotificationItem) -> Unit,
    onCommentLike: (NotificationItem) -> Unit,
    onFollowBack: (NotificationItem) -> Unit,
    onMarkAllRead: () -> Unit,
    onRetry: () -> Unit,
) {
    var confirmReadAll by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(XhsColor.Bg),
        ) {
            XhsTopBar(
                title = categoryTitle(state.category),
                onBack = onBack,
                actions = {
                    // 🧹 一键已读（线框 G2/G3/G4 右上）
                    XhsIconButton(
                        iconRes = R.drawable.ic_broom,
                        onClick = { confirmReadAll = true },
                        contentDescription = "一键已读",
                    )
                },
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
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(items = state.page.items, key = { it.id }) { item ->
                            NotificationRow(
                                item = item,
                                category = state.category,
                                reply = state.reply,
                                followed = item.senderId in state.followedSenders,
                                commentLiked = item.commentId in state.likedCommentIds,
                                onRowClick = { onRowClick(item) },
                                onAvatarClick = { onAvatarClick(item) },
                                onReplyToggle = { onReplyToggle(item) },
                                onReplyChange = onReplyChange,
                                onReplySend = { onReplySend(item) },
                                onCommentLike = { onCommentLike(item) },
                                onFollowBack = { onFollowBack(item) },
                            )
                        }
                        item { XhsListFooter(state = state.page) }
                    }
                }
            }
        }

        // G6 一键已读确认（与 F6 同款结构；遮罩下为当前列表页）
        XhsConfirmSheet(
            visible = confirmReadAll,
            onConfirm = onMarkAllRead,
            onDismiss = { confirmReadAll = false },
            message = "确认将全部消息标记为已读吗？",
            confirmText = "一键已读",
            cancelText = "取消",
        )
    }
}

/** G2/G3/G4 顶栏标题（线框实测文案）。 */
private fun categoryTitle(category: NotificationCategory): String = when (category) {
    NotificationCategory.LIKE_COLLECT -> "收到的赞和收藏"
    NotificationCategory.COMMENT -> "收到的评论和@"
    NotificationCategory.FOLLOW -> "新增关注"
}
