package com.xiaohongshu.app.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohongshu.app.core.net.ApiResult
import com.xiaohongshu.app.core.net.userMessage
import com.xiaohongshu.app.data.repo.AiRepository
import com.xiaohongshu.app.domain.model.ChatMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** H1/H2/H3 点点对话的 UI 状态。 */
data class AiUiState(
    /** 对话历史（**仅内存**；「新建对话」清空回 H1，见契约 §11）。 */
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    /** H3-1 思考中：三点脉冲动画，期间输入与发送禁用。 */
    val thinking: Boolean = false,
    /** H1 建议问题 chips；初值即本地兜底，接口成功后替换（保证 H1 永不空窗）。 */
    val suggestions: List<String> = AiRepository.FALLBACK_SUGGESTIONS,
) {
    /** H1 空态：没有任何消息且不在思考中。 */
    val isEmpty: Boolean get() = messages.isEmpty() && !thinking

    /** 输入非空且不在思考中才可发送。 */
    val canSend: Boolean get() = input.isNotBlank() && !thinking
}

/**
 * 点点对话的 ViewModel（H1/H2/H3）。
 *
 * 单轮问答（契约 §10.1）：**每次只把当前问题发给服务端**，不携带历史；
 * [state] 里的 `messages` 只用于本地展示，进程结束即丢失。
 */
class AiViewModel(private val repository: AiRepository) : ViewModel() {

    private val _state = MutableStateFlow(AiUiState())
    val state: StateFlow<AiUiState> = _state.asStateFlow()

    /** 本地自增 id：保证 LazyColumn 的 key 唯一（服务端返回的 id 不参与 key）。 */
    private var seq = 0L

    /** 进行中的问答；「新建对话」时取消，避免旧回答追进新会话。 */
    private var pending: Job? = null

    /** H1 建议问题：接口失败/返回空 → 保留本地兜底文案（契约 §10.2「不阻塞」）。 */
    fun loadSuggestions() {
        viewModelScope.launch {
            val result = repository.suggestions()
            if (result is ApiResult.Ok) {
                val items = result.data.filter { it.isNotBlank() }
                if (items.isNotEmpty()) _state.update { it.copy(suggestions = items) }
            }
        }
    }

    /** 输入变更：超出 4000 字符的部分直接截断（契约 §10.1 `message ≤4000`）。 */
    fun onInputChange(text: String) {
        _state.update { it.copy(input = text.take(MAX_INPUT_LENGTH)) }
    }

    /** 发送输入框里的内容。 */
    fun sendInput() = send(_state.value.input)

    /**
     * 发送一条问题（输入框回车/发送钮、H1 建议问题 chip 共用）。
     *
     * 流程：追加用户气泡 → 置思考中（H3-1）→ 单轮请求 → 成功追加 AI 卡片（H2）/
     * 失败追加**对话流内的红色气泡**（H3-2，超时文案由 `ApiResult.userMessage()` 给出）。
     * 失败不中断会话：历史保留、输入恢复可用（`thinking = false`）。
     */
    fun send(question: String) {
        val text = question.trim().take(MAX_INPUT_LENGTH)
        if (text.isEmpty() || _state.value.thinking) return

        val userMessage = ChatMessage(
            id = nextId(),
            fromUser = true,
            text = text,
            state = ChatMessage.ChatState.Done,
        )
        _state.update {
            it.copy(messages = it.messages + userMessage, input = "", thinking = true)
        }

        pending = viewModelScope.launch {
            val result = repository.chat(text)
            if (result is ApiResult.Ok) {
                val replyId = nextId()
                _state.update {
                    it.copy(messages = it.messages + result.data.copy(id = replyId), thinking = false)
                }
            } else {
                val errorId = nextId()
                _state.update {
                    it.copy(
                        messages = it.messages + ChatMessage(
                            id = errorId,
                            fromUser = false,
                            text = result.userMessage(),
                            state = ChatMessage.ChatState.Failed,
                        ),
                        thinking = false,
                    )
                }
            }
        }
    }

    /** 「⋯」→「新建对话」：清空内存对话回到 H1（建议问题保留）。 */
    fun newConversation() {
        pending?.cancel()
        pending = null
        _state.update { it.copy(messages = emptyList(), input = "", thinking = false) }
    }

    private fun nextId(): Long = ++seq

    companion object {
        /** 单条消息上限（契约 §10.1：≤4000）。 */
        const val MAX_INPUT_LENGTH = 4000
    }
}
