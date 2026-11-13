package com.xiaohongshu.app.feature.message

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.design.XhsType
import com.xiaohongshu.app.core.ui.XhsAvatar
import com.xiaohongshu.app.core.ui.XhsEmptyState
import com.xiaohongshu.app.core.ui.XhsPageLoading
import com.xiaohongshu.app.core.ui.XhsRetryPill
import com.xiaohongshu.app.core.ui.XhsTopBar
import com.xiaohongshu.app.di.LocalAppContainer
import com.xiaohongshu.app.di.appViewModel
import com.xiaohongshu.app.navigation.AppNavigator

/**
 * G8 私聊页（契约 §12；推入式，**无底部 Tab**）。
 *
 * 入口：G1 会话行点击、F2 他人主页「私信」按钮（先 `POST /api/chat/conversations`
 * 幂等拿会话 id 再推本页）。peer 信息由路由参数带入（建会话响应里已有，省一次查询）。
 *
 * 进入即拉最近 20 条历史 + 静默已读上报；WS 在线时对方消息实时上屏并滚底。
 */
@Composable
fun ChatRoute(
    navigator: AppNavigator,
    conversationId: Long,
    peerId: Long,
    peerNickname: String,
    peerAvatar: String,
) {
    val container = LocalAppContainer.current
    val session by container.sessionManager.state.collectAsStateWithLifecycle()
    val vm: ChatViewModel = appViewModel(key = "chat-$conversationId") {
        ChatViewModel(
            conversationId = conversationId,
            peerId = peerId,
            peerNickname = peerNickname,
            peerAvatar = peerAvatar,
            repository = it.chatRepository,
            realtime = it.realtimeConnection,
            session = it.sessionManager,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()

    ChatScreen(
        state = state,
        myAvatar = session.user.avatar,
        onBack = navigator::back,
        onInputChange = vm::onInputChange,
        onSend = vm::send,
        onRetryMessage = vm::retry,
        onRetryPage = vm::retry,
        onPeerClick = { navigator.toUserProfile(state.peerId) },
    )
}

/** 无状态内容层：本人头像与消息身份共用同一登录态。 */
@Composable
private fun ChatScreen(
    state: ChatUiState,
    myAvatar: String,
    onBack: () -> Unit,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onRetryMessage: (com.xiaohongshu.app.domain.model.ImMessage) -> Unit,
    onRetryPage: () -> Unit,
    onPeerClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(XhsColor.Bg),
        ) {
            // 顶栏 56：返回 + 中央（头像 32 + 昵称，整体可点 → F2）
            XhsTopBar(
                height = Dimens.detailBar,
                onBack = onBack,
                showDivider = false,
                center = {
                    Row(
                        modifier = Modifier.clickable(onClick = onPeerClick),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        XhsAvatar(
                            url = state.peerAvatar,
                            size = Dimens.avatarConversation / 2,
                        )
                        Spacer(modifier = Modifier.width(Dimens.s8))
                        Text(
                            text = state.peerNickname,
                            style = XhsType.pageTitle,
                            color = XhsColor.Text1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
            )

            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.loading -> ChatLoading()

                    state.error != null && state.messages.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        XhsEmptyState(
                            text = state.error,
                            action = { XhsRetryPill(onRetry = onRetryPage) },
                        )
                    }

//                    state.messages.isEmpty() -> Box(
//                        modifier = Modifier.fillMaxSize(),
//                        contentAlignment = Alignment.Center,
//                    ) {
//                        XhsEmptyState(text = "还没有消息，打个招呼吧")
//                    }

                    else -> ChatMessageList(
                        messages = state.messages,
                        loadingEarlier = state.loadingEarlier,
                        myAvatar = myAvatar,
                        peerAvatar = state.peerAvatar,
                        onRetry = onRetryMessage,
                    )
                }
            }

            ChatInputBar(
                input = state.input,
                onInputChange = onInputChange,
                onSend = onSend,
            )
        }
    }
}

/** 首刷加载中（I3 页面级）。 */
@Composable
private fun ChatLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        XhsPageLoading()
    }
}
