package com.xiaohongshu.app.feature.message

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohongshu.app.core.list.PagedList
import com.xiaohongshu.app.core.list.PagedState
import com.xiaohongshu.app.core.net.onOk
import com.xiaohongshu.app.core.realtime.RealtimeConnection
import com.xiaohongshu.app.core.realtime.RealtimeEvent
import com.xiaohongshu.app.core.ui.ToastController
import com.xiaohongshu.app.data.local.SessionManager
import com.xiaohongshu.app.data.repo.ChatRepository
import com.xiaohongshu.app.domain.model.Conversation
import com.xiaohongshu.app.domain.model.ImMessage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** G1 会话列表 UI 状态。 */
internal data class ConversationsUiState(
    val items: List<Conversation> = emptyList(),
    val state: PagedState<Conversation> = PagedState(),
) {
    val loading: Boolean get() = state.loading
    val error: String? get() = state.error
}

/**
 * G1 私信会话列表（契约 §12.2）。
 *
 * 数据来源：
 * - 首屏 / Tab 回到前台：`GET /api/chat/conversations`（[conversations] 分页引擎）；
 * - 运行中：订阅 [RealtimeConnection] 的 `chat.msg` 帧——命中的会话**置顶**并更新预览/
 *   时间/未读数（+1）；不在列表里的新会话（刚发首条消息）静默刷新首页校正。
 *
 * 这是 Tab 根页面，失败**不**渲染错误页/空态（列表为空 = 只有「点点」一行），
 * 只静默保留旧内容，下次进 Tab 重试。
 */
internal class ConversationsViewModel(
    private val repository: ChatRepository,
    private val realtime: RealtimeConnection,
    private val session: SessionManager,
    toasts: ToastController,
) : ViewModel() {

    private val conversations = PagedList<Conversation>(
        keyOf = { it.id },
        toasts = toasts,
        fetch = { page, size -> repository.conversations(page = page, pageSize = size) },
    )

    val state: StateFlow<ConversationsUiState> = conversations.state
        .map { s -> ConversationsUiState(items = s.items, state = s) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ConversationsUiState(state = PagedState(loading = true)),
        )

    init {
        // WS 新消息：命中会话就地更新（置顶 + 预览 + 未读 +1）
        realtime.events
            .onEach { event ->
                if (event is RealtimeEvent.NewMessage) onNewMessage(event.message)
            }
            .launchIn(viewModelScope)
    }

    private suspend fun onNewMessage(message: ImMessage) {
        val current = conversations.state.value.items
        val existing = current.firstOrNull { it.id == message.conversationId }
        if (existing == null) {
            // 列表里没有这个会话（刚建/首条消息）：静默拉第一页校正
            refresh()
            return
        }
        // 我发的消息不产生未读；对方发来的 +1
        val isMine = message.senderId == session.currentUserId
        val updated = existing.copy(
            lastMessage = message.content,
            lastTime = message.time,
            unread = if (isMine) existing.unread else existing.unread + 1,
        )
        conversations.mutate { list ->
            listOf(updated) + list.filterNot { it.id == updated.id }
        }
    }

    /** 进 Tab / 返回本 Tab 时首刷。 */
    fun refresh() {
        viewModelScope.launch { conversations.refresh() }
    }

    fun loadMore() {
        viewModelScope.launch { conversations.loadMore() }
    }

    fun retry() {
        viewModelScope.launch { conversations.retry() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
