package com.xiaohongshu.app.feature.message

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohongshu.app.core.realtime.RealtimeConnection
import com.xiaohongshu.app.core.realtime.RealtimeEvent
import com.xiaohongshu.app.data.local.SessionManager
import com.xiaohongshu.app.core.net.ApiResult
import com.xiaohongshu.app.core.net.userMessage
import com.xiaohongshu.app.data.repo.ChatRepository
import com.xiaohongshu.app.domain.model.ImMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.UUID

/** G8 私聊页 UI 状态。 */
internal data class ChatUiState(
    val conversationId: Long = 0,
    val peerId: Long = 0,
    val peerNickname: String = "",
    val peerAvatar: String = "",
    /** 按时间正序（最旧在前）；含本地临时消息（id < 0）。 */
    val messages: List<ImMessage> = emptyList(),
    val loading: Boolean = false,
    /** 首次加载失败文案（可展示重试）；null = 正常。 */
    val error: String? = null,
    val input: String = "",
    /** 向上翻页加载更早消息中。 */
    val loadingEarlier: Boolean = false,
    /** 更早的历史已到底。 */
    val noMoreEarlier: Boolean = false,
)

/**
 * G8 私聊页（契约 §12.3/§12.5/§12.6/§12.7/§12.8）。
 *
 * 数据来源：
 * - 历史：`GET /api/chat/{id}/messages`（分页按 id 升序，page 越小越新；
 *   故 [loadEarlier] 递减 page 并向**头部**拼接）；
 * - 发送：`POST .../messages` 带 [clientId][ImMessage.clientId]（服务端幂等去重，
 *   失败重试带同一个 clientId）；
 * - 已读：`PUT .../read`（进入会话 + 收到对方新消息时静默上报）；
 * - 实时：订阅 [RealtimeConnection] 的 `chat.msg`——本会话的消息直接上屏，
 *   **自己发的**（多设备回显）按 id 去重跳过。
 *
 * 乐观发送：本地临时消息 id 用 [LocalIdSeed] 取负数，绝不与服务端 id 冲突；
 * 成功后按 clientId 匹配替换，失败置 [ImMessage.SendState.Failed]（点气泡重发）。
 */
internal class ChatViewModel(
    private val conversationId: Long,
    peerId: Long,
    peerNickname: String,
    peerAvatar: String,
    private val repository: ChatRepository,
    private val realtime: RealtimeConnection,
    private val session: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ChatUiState(
            conversationId = conversationId,
            peerId = peerId,
            peerNickname = peerNickname,
            peerAvatar = peerAvatar,
            loading = true,
        ),
    )
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    /** 已加载到的最老一页（page 越小越新；0 = 尚未加载）。 */
    private var oldestPage = 0

    init {
        // 实时推送：只接本会话的消息；ReadReceipt 本版不渲染（契约 §12.3 简化）
        realtime.events
            .onEach { event ->
                if (event is RealtimeEvent.NewMessage &&
                    event.message.conversationId == conversationId
                ) {
                    onRemoteMessage(event.message)
                }
            }
            .launchIn(viewModelScope)

        loadInitial()
    }

    /** 进入会话：拉最近 20 条 + 静默已读上报。 */
    private fun loadInitial() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            when (val result = repository.messages(conversationId, page = 1, pageSize = PAGE_SIZE)) {
                is ApiResult.Ok -> {
                    oldestPage = result.data.page
                    _state.value = _state.value.copy(
                        messages = result.data.items.sortedBy { it.id },
                        loading = false,
                        error = null,
                        noMoreEarlier = !result.data.hasMore,
                    )
                    markRead()
                }
                else -> {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = result.userMessage(),
                    )
                }
            }
        }
    }

    fun retry() = loadInitial()

    /** 向上翻页：拉更早一页，拼到头部。 */
    fun loadEarlier() {
        val current = _state.value
        if (current.loadingEarlier || current.noMoreEarlier || current.loading) return
        val nextPage = oldestPage + 1
        viewModelScope.launch {
            _state.value = current.copy(loadingEarlier = true)
            when (val result = repository.messages(conversationId, page = nextPage, pageSize = PAGE_SIZE)) {
                is ApiResult.Ok -> {
                    if (result.data.items.isEmpty()) {
                        _state.value = _state.value.copy(
                            loadingEarlier = false,
                            noMoreEarlier = true,
                        )
                        return@launch
                    }
                    oldestPage = result.data.page
                    _state.value = _state.value.copy(
                        messages = (result.data.items + _state.value.messages).distinctBy { it.id }
                            .sortedBy { it.id },
                        loadingEarlier = false,
                        noMoreEarlier = !result.data.hasMore,
                    )
                }
                else -> _state.value = _state.value.copy(loadingEarlier = false)
            }
        }
    }

    // ------------------------------------------------------------------ 输入与发送

    fun onInputChange(text: String) {
        _state.value = _state.value.copy(input = text)
    }

    /** 发送（乐观）。空白与超长在本地拦下，不请求。 */
    fun send() {
        val content = _state.value.input.trim()
        if (content.isEmpty() || content.length > MAX_CONTENT_LEN) return
        val clientId = UUID.randomUUID().toString()
        _state.value = _state.value.copy(input = "")
        val optimistic = ImMessage(
            id = ImMessage.LocalIdSeed--,
            conversationId = conversationId,
            senderId = session.currentUserId,
            content = content,
            time = System.currentTimeMillis(),
            clientId = clientId,
            state = ImMessage.SendState.Sending,
        )
        _state.value = _state.value.copy(messages = _state.value.messages + optimistic)
        dispatch(optimistic)
    }

    /** 失败气泡点按重试：同一 clientId 重发（服务端幂等）。 */
    fun retry(message: ImMessage) {
        if (message.state != ImMessage.SendState.Failed) return
        val pending = message.copy(state = ImMessage.SendState.Sending)
        replace(pending)
        dispatch(pending)
    }

    private fun dispatch(message: ImMessage) {
        viewModelScope.launch {
            when (val result = repository.sendMessage(
                conversationId = conversationId,
                content = message.content,
                clientId = message.clientId,
            )) {
                is ApiResult.Ok -> {
                    val server = result.data
                    // 按 clientId 匹配替换；WS 已提前推回同一条（多设备）时按 id 去重
                    val exists = _state.value.messages.any { it.id == server.id }
                    _state.value = _state.value.copy(
                        messages = if (exists) {
                            _state.value.messages.filterNot { it.id == message.id }
                        } else {
                            _state.value.messages
                                .map { if (it.id == message.id) server else it }
                        },
                    )
                }
                else -> replace(message.copy(state = ImMessage.SendState.Failed))
            }
        }
    }

    private fun replace(message: ImMessage) {
        _state.value = _state.value.copy(
            messages = _state.value.messages.map { if (it.id == message.id) message else it },
        )
    }

    // ------------------------------------------------------------------ 实时消息

    /**
     * WS 推送的新消息：id 已存在则跳过（去重自己消息的多设备回显 / 历史里已有的）；
     * 否则追加 + 静默已读（会话在前台 = 已读）。
     */
    private suspend fun onRemoteMessage(message: ImMessage) {
        if (_state.value.messages.any { it.id == message.id }) return
        _state.value = _state.value.copy(messages = _state.value.messages + message)
        // 只有对方发来的才需要上报已读；自己多设备的回显不影响未读数
        if (message.senderId != session.currentUserId) markRead()
    }

    private suspend fun markRead() {
        repository.markRead(conversationId)
    }

    private companion object {
        const val PAGE_SIZE = 20
        const val MAX_CONTENT_LEN = 500
    }
}
