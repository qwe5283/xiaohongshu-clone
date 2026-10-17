package com.xiaohongshu.app.core.realtime

import android.util.Log
import com.xiaohongshu.app.core.net.ApiClient
import com.xiaohongshu.app.core.net.TokenProvider
import com.xiaohongshu.app.core.util.Formatters
import com.xiaohongshu.app.domain.model.ImMessage
import com.xiaohongshu.app.domain.model.UnreadCounts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlin.math.pow

/** WS 下行事件（core/realtime 对业务层的唯一出口）。 */
sealed interface RealtimeEvent {

    /** 新消息推送（chat.msg）。含发送者自己多设备回显的消息——消费方按 id 去重。 */
    data class NewMessage(val message: ImMessage) : RealtimeEvent

    /** 已读回执（chat.read）。本版不渲染已读态，事件保留供后续使用。 */
    data class ReadReceipt(val conversationId: Long, val readerId: Long) : RealtimeEvent

    /** 通知未读数变化（notify.unread）——未读数的增量推送通道（REST 仅关键时刻校正）。 */
    data class NotifyUnread(val counts: UnreadCounts) : RealtimeEvent

    /** 连接状态变化。重连成功（connected=true）时业务层应拉一次 REST 校正。 */
    data class Connected(val connected: Boolean) : RealtimeEvent
}

/**
 * IM 实时通道（契约 §12.7）：`ws(s)://<host>/ws/chat?token=<JWT>`。
 *
 * 职责边界：
 * - **一条连接 = 一个登录用户**，多路复用所有会话的下行推送（chat.msg / chat.read /
 *   notify.unread）；连接维度不是会话。
 * - HTTP 管状态、WS 管增量：离线消息由 REST 重连后补齐，WS 不承诺补投。
 * - 断线/握手失败一律**静默退避重试**，绝不触发登录失效（I1 只认 REST 401）——
 *   WS 是增强通道，握手被服务端拒绝多半是 token 刚过期，重连前会重新读 tokenHolder。
 * - 自持应用级 [scope]：**不挂页面 VM 的 scope**，否则退页销毁会打断重连。
 *
 * 心跳：连上后每 [PING_INTERVAL_MS] 发 `ping`；[PONG_TIMEOUT_MS] 未收到 `pong` 判定为僵死，
 * 主动断开并走重连。服务端侧 90s 无帧也会断开，两端机制互为兜底。
 */
class RealtimeConnection(
    private val baseUrl: String,
    private val tokenProvider: TokenProvider,
    private val client: OkHttpClient,
) {

    private val scope = CoroutineScope(SupervisorJob())

    private val _events = MutableSharedFlow<RealtimeEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<RealtimeEvent> = _events.asSharedFlow()

    private var socket: WebSocket? = null
    private var connectJob: Job? = null
    private var pingJob: Job? = null

    /** 连接中标记：connect() 已调用、尚未 disconnect()。 */
    private var wantConnected = false

    /** 连续重连次数，用于指数退避。 */
    private val retryCount = AtomicInteger(0)

    /** 最近一次收到任意帧的时间；心跳协程据此判定僵死。 */
    @Volatile
    private var lastFrameAt = 0L

    /** 建连 URL：与 HTTP 同 host，scheme 换成 ws/wss。 */
    private fun wsUrl(): String? {
        val token = tokenProvider.token()?.takeIf { it.isNotBlank() } ?: return null
        val wsBase = baseUrl
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
            .trimEnd('/')
        return "$wsBase/ws/chat?token=$token"
    }

    /** 前台生命周期进入（ON_START）：已登录则建连。 */
    fun connect() {
        if (wantConnected) return
        wantConnected = true
        retryCount.set(0)
        openSocket()
    }

    /** 前台生命周期离开（ON_STOP）或退出登录：断开并取消重连。 */
    fun disconnect() {
        wantConnected = false
        connectJob?.cancel()
        pingJob?.cancel()
        runCatching { socket?.close(NORMAL_CLOSURE, "bye") }
        socket = null
    }

    private fun openSocket() {
        if (!wantConnected) return
        val url = wsUrl()
        if (url == null) {
            // 未登录：无 token 可带。等 3s 再看（登录流程会写 tokenHolder）
            scheduleRetry(TOKEN_WAIT_MS)
            return
        }
        val request = Request.Builder().url(url).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                retryCount.set(0)
                lastFrameAt = System.currentTimeMillis()
                startPingLoop()
                emit(RealtimeEvent.Connected(true))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                lastFrameAt = System.currentTimeMillis()
                handleFrame(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // 握手被拒（401/404）或网络级断开：静默退避重试（静默＝不触发登录失效，
                // 不是不发事件）。断开与 onClosed 一样广播——移动端断线大多走这里而非
                // onClosed，漏发会让 Connected 事件与实际连接状态不一致。
                webSocket.cancel()
                stopPingLoop()
                emit(RealtimeEvent.Connected(false))
                if (wantConnected) scheduleRetry(backoffMs())
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                stopPingLoop()
                emit(RealtimeEvent.Connected(false))
                if (wantConnected) scheduleRetry(backoffMs())
            }
        })
    }

    /** 指数退避：1s 起步，每次翻倍，上限 60s。 */
    private fun backoffMs(): Long {
        val n = retryCount.getAndIncrement()
        return min(MAX_BACKOFF_MS, BASE_BACKOFF_MS * 2.0.pow(n.toDouble()).toLong())
    }

    private fun scheduleRetry(delayMs: Long) {
        connectJob?.cancel()
        connectJob = scope.launch {
            delay(delayMs)
            openSocket()
        }
    }

    private fun startPingLoop() {
        stopPingLoop()
        pingJob = scope.launch {
            while (isActive) {
                delay(PING_INTERVAL_MS)
                // 60s 未收到任何帧 → 判定僵死，断开走重连
                if (System.currentTimeMillis() - lastFrameAt > PONG_TIMEOUT_MS) {
                    Log.w(TAG, "ws stale (>${PONG_TIMEOUT_MS}ms), reconnecting")
                    runCatching { socket?.cancel() }
                    return@launch
                }
                runCatching { socket?.send(pingFrame()) }
            }
        }
    }

    private fun stopPingLoop() {
        pingJob?.cancel()
        pingJob = null
    }

    private fun emit(event: RealtimeEvent) {
        _events.tryEmit(event)
    }

    // ---------------------------------------------------------------- 帧解析

    private fun handleFrame(text: String) {
        val root = runCatching { ApiClient.json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: return
        when (root["type"]?.jsonPrimitive?.contentOrNull) {
            "hello" -> Unit // 鉴权通过确认，无后续动作
            "pong" -> Unit // 心跳应答
            "chat.msg" -> root.decodeChatMessage()?.let { emit(RealtimeEvent.NewMessage(it)) }
            "chat.read" -> {
                val d = root["data"]?.jsonObject
                val convId = d?.get("conversationId")?.jsonPrimitive?.longOrNull ?: return
                val reader = d["readerId"]?.jsonPrimitive?.longOrNull ?: return
                emit(RealtimeEvent.ReadReceipt(convId, reader))
            }
            "notify.unread" -> root.decodeNotifyUnread()?.let { emit(RealtimeEvent.NotifyUnread(it)) }
            else -> Unit // error 帧与未知 type：忽略（服务端不因坏帧踢连接）
        }
    }

    /** chat.msg 的 data 就是 ChatMessageVO（契约 §12.1）。 */
    private fun JsonObject.decodeChatMessage(): ImMessage? {
        val d = this["data"]?.jsonObject ?: return null
        return ImMessage(
            id = d["id"]?.jsonPrimitive?.longOrNull ?: return null,
            conversationId = d["conversationId"]?.jsonPrimitive?.longOrNull ?: return null,
            senderId = d["senderId"]?.jsonPrimitive?.longOrNull ?: return null,
            content = d["content"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            time = Formatters
                .parseApiTime(d["createTime"]?.jsonPrimitive?.contentOrNull.orEmpty()),
            state = ImMessage.SendState.Sent,
        )
    }

    /** notify.unread 的 data 与 §7.1 unread-count 响应同形。 */
    private fun JsonObject.decodeNotifyUnread(): UnreadCounts? {
        val d = this["data"]?.jsonObject ?: return null
        return UnreadCounts(
            total = d["unreadCount"]?.jsonPrimitive?.longOrNull ?: 0,
            likeCollect = d["likeUnread"]?.jsonPrimitive?.longOrNull ?: 0,
            comment = d["commentUnread"]?.jsonPrimitive?.longOrNull ?: 0,
            follow = d["followUnread"]?.jsonPrimitive?.longOrNull ?: 0,
        )
    }

    private fun pingFrame(): String =
        Json.encodeToString(PingFrame.serializer(), PingFrame())

    @Serializable
    private data class PingFrame(
        val type: String = "ping",
        val ts: Long = System.currentTimeMillis(),
        val data: JsonObject = JsonObject(emptyMap()),
    )

    companion object {
        private const val TAG = "RealtimeConnection"

        /** 心跳间隔 30s（契约 §12.7 客户端行为）。 */
        const val PING_INTERVAL_MS = 30_000L

        /** 60s 收不到任何帧判定僵死。 */
        const val PONG_TIMEOUT_MS = 60_000L

        private const val BASE_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 60_000L

        /** 未登录（无 token）时的重试间隔。 */
        private const val TOKEN_WAIT_MS = 3_000L

        private const val NORMAL_CLOSURE = 1000
    }
}
