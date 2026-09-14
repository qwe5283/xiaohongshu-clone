package com.xiaohongshu.app.core.auth

import com.xiaohongshu.app.data.local.SessionManager

/**
 * 登录拦截（线框 A2 → A3 → A6）。
 *
 * 规则：游客可以浏览首页瀑布流/搜索/笔记详情及评论/他人主页；**任何写操作**
 * （点赞/收藏/评论/关注）与「＋」「点点」「消息」「我」入口 → 直接推入登录页，
 * **不做 Toast 提示**；登录成功后**弹回原位置**。
 *
 * 用 [runOrDefer] 统一处理：已登录立即执行；未登录把动作暂存，登录成功后由导航层
 * 调 [consumePending] 补执行——这样「游客点♥ → 登录 → 回来时赞已经点上」，
 * 而不是白点一次。
 */
class LoginGate(private val session: SessionManager) {

    private var pending: (() -> Unit)? = null

    /**
     * 尝试执行需要登录的动作。
     *
     * @return true = 已登录且已执行；false = 未登录，动作已暂存，调用方应推入登录页。
     */
    fun runOrDefer(action: () -> Unit): Boolean {
        if (session.isLoggedIn) {
            action()
            return true
        }
        pending = action
        return false
    }

    /** 仅判断是否需要登录（用于「＋ / 消息 / 我」这类入口拦截）。 */
    fun requireLogin(): Boolean = session.isLoggedIn

    /** A6：登录成功后由导航层调用，补执行被拦截的动作。 */
    fun consumePending() {
        val action = pending
        pending = null
        action?.invoke()
    }

    /** 放弃登录（返回来源页）时丢弃暂存动作。 */
    fun clear() {
        pending = null
    }
}
