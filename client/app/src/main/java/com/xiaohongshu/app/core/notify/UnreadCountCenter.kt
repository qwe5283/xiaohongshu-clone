package com.xiaohongshu.app.core.notify

import com.xiaohongshu.app.core.net.onOk
import com.xiaohongshu.app.core.realtime.RealtimeConnection
import com.xiaohongshu.app.core.realtime.RealtimeEvent
import com.xiaohongshu.app.data.local.SessionManager
import com.xiaohongshu.app.data.repo.NotificationRepository
import com.xiaohongshu.app.domain.model.UnreadCounts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 未读消息中心（G1 / 底 Tab 角标）。
 *
 * 规范：>99 显示 `99+`；自己操作自己不产生通知（后端保证）；点条目先标已读再跳转，
 * 角标随之递减。
 *
 * **通道（契约 §12.7）**：未读数由 WS `notify.unread` 帧**实时推送**；REST 只在关键时刻
 * 校正——登录、进消息 Tab、从通知页返回、WS 重连成功（[refresh]）。**不做轮询**：
 * 断线期间角标保持上次值，重连成功的那一刻由 [refresh] 全量校正（取舍记录见交付说明）。
 *
 * 本地乐观更新（[decrement]/[clearCategory]/[clearAll]）先本地生效，
 * 最终以下次推送/校正为准。
 *
 * 生命周期：只在「已登录 + App 处于前台」时收推送；退出登录立即清零。
 */
class UnreadCountCenter(
    private val repository: NotificationRepository,
    private val session: SessionManager,
    private val realtime: RealtimeConnection,
) {

    private val _counts = MutableStateFlow(UnreadCounts.Empty)
    val counts: StateFlow<UnreadCounts> = _counts.asStateFlow()

    private var realtimeJob: Job? = null

    /** 在 App 进入前台时启动（`AppNavHost` 的 ON_START）。 */
    fun start(scope: CoroutineScope) {
        if (realtimeJob?.isActive == true) return

        // 推送通道：notify.unread 帧 + 重连成功后的 HTTP 校正
        realtimeJob = scope.launch {
            realtime.events.collect { event ->
                when (event) {
                    is RealtimeEvent.NotifyUnread -> _counts.value = event.counts
                    is RealtimeEvent.Connected -> if (event.connected) {
                        // 重连成功：立刻用 HTTP 校正一次（断线期间的增量以此为准）
                        refresh()
                    }
                    else -> Unit // chat.msg / chat.read / 断开事件与未读数无关
                }
            }
        }
    }

    /** App 进入后台时取消推送收集（WS 由导航层统一断开）。 */
    fun stop() {
        realtimeJob?.cancel()
        realtimeJob = null
    }

    /** 立即拉取一次（登录成功、从通知页返回、WS 重连后、进消息 Tab 时调用）。 */
    suspend fun refresh() {
        if (!session.isLoggedIn) {
            _counts.value = UnreadCounts.Empty
            return
        }
        repository.unreadCounts().onOk { _counts.value = it }
    }

    /** 退出登录时清零。 */
    fun clear() {
        _counts.value = UnreadCounts.Empty
    }

    /**
     * 单条已读的本地乐观递减：角标立刻 -1，最终以下次推送/校正结果为准。
     * （标记失败不阻断操作，下次校正——G5-2 异常恢复说明。）
     */
    fun decrement(categoryValue: Int) {
        val current = _counts.value
        if (current.total <= 0) return
        _counts.value = when (categoryValue) {
            1 -> current.copy(
                total = (current.total - 1).coerceAtLeast(0),
                likeCollect = (current.likeCollect - 1).coerceAtLeast(0),
            )
            2 -> current.copy(
                total = (current.total - 1).coerceAtLeast(0),
                comment = (current.comment - 1).coerceAtLeast(0),
            )
            3 -> current.copy(
                total = (current.total - 1).coerceAtLeast(0),
                follow = (current.follow - 1).coerceAtLeast(0),
            )
            else -> current
        }
    }

    /** G6 一键已读：整类清零。 */
    fun clearCategory(categoryValue: Int) {
        val current = _counts.value
        _counts.value = when (categoryValue) {
            1 -> current.copy(
                total = (current.total - current.likeCollect).coerceAtLeast(0),
                likeCollect = 0,
            )
            2 -> current.copy(
                total = (current.total - current.comment).coerceAtLeast(0),
                comment = 0,
            )
            3 -> current.copy(
                total = (current.total - current.follow).coerceAtLeast(0),
                follow = 0,
            )
            else -> UnreadCounts.Empty
        }
    }

    /**
     * G1 一键已读（入口移入 G1 后作用范围为**全部三分类**）：本地立即全清，
     * 服务端结果以下次推送/校正为准。
     */
    fun clearAll() {
        _counts.value = UnreadCounts.Empty
    }
}
