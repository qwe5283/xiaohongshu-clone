package com.xiaohongshu.app.core.notify

import com.xiaohongshu.app.core.net.onOk
import com.xiaohongshu.app.data.local.SessionManager
import com.xiaohongshu.app.data.repo.NotificationRepository
import com.xiaohongshu.app.domain.model.UnreadCounts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 未读消息中心（G1 / 底 Tab 角标）。
 *
 * 线框规范：未读角标每 **15 秒**轮询；>99 显示 `99+`；自己操作自己不产生通知（后端保证）；
 * 点条目先标已读再跳转，角标随之递减。
 *
 * 轮询只在「已登录 + App 处于前台」时进行；退出登录立即清零。
 * 「点条目先标已读」用 [decrement] 做本地乐观递减，避免等下一次轮询才更新角标。
 */
class UnreadCountCenter(
    private val repository: NotificationRepository,
    private val session: SessionManager,
) {

    private val _counts = MutableStateFlow(UnreadCounts.Empty)
    val counts: StateFlow<UnreadCounts> = _counts.asStateFlow()

    private var job: Job? = null

    /** 在 App 进入前台时启动轮询。 */
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                refresh()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** App 进入后台时停止轮询，避免无谓流量。 */
    fun stop() {
        job?.cancel()
        job = null
    }

    /** 立即拉取一次（登录成功、从通知页返回时调用）。 */
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
     * 单条已读的本地乐观递减：角标立刻 -1，最终以轮询结果为准。
     * （标记失败不阻断操作，下次轮询校正——线框 G5-2 异常恢复说明。）
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

    companion object {
        /** 轮询间隔（线框「通知」规范：每 15 秒）。 */
        const val POLL_INTERVAL_MS = 15_000L
    }
}
