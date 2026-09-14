package com.xiaohongshu.app.navigation

import com.xiaohongshu.app.data.dto.NotificationCategory

/**
 * 页面跳转接口 —— 各 feature 唯一的导航依赖。
 *
 * 设计取舍：feature 不直接依赖 NavController，而是依赖这个接口。好处是
 * ① feature 之间零耦合（可并行开发、互不引用）；
 * ② 跳转语义集中在一处，路由表变更不影响各页面；
 * ③ 便于在 Preview / 测试里传入空实现。
 */
interface AppNavigator {

    /** 返回上一页；栈底为 no-op。 */
    fun back()

    /** 回到 Tab 宿主（发布成功 E5-2、放弃发布 E7 用）。 */
    fun toMain()

    /** A3 推入登录页（游客拦截 A2 / 会话过期 I1）。 */
    fun toLogin()

    /** A4 推入注册页。 */
    fun toRegister()

    /** 登录/注册成功后弹回来源页（A6）。 */
    fun popLogin()

    /** B2 搜索页。 */
    fun toSearch()

    /** B3-1 搜索结果页（带关键词）。 */
    fun toSearchResult(keyword: String)

    /** C1-1 图文详情。 */
    fun toNoteDetail(postId: Long)

    /**
     * 笔记详情：按笔记类型自动分流到 C1-1 或 C2-1。
     * 首页/搜索/主页卡片与服务端都只给 postId，由实现方查类型后决定，避免各调用点重复判断。
     */
    fun toNote(postId: Long, isVideo: Boolean)

    /** C2-1 视频详情。 */
    fun toVideoDetail(postId: Long)

    /** F2 他人主页。 */
    fun toUserProfile(userId: Long)

    /** F3 编辑资料。 */
    fun toEditProfile()

    /** F5 设置。 */
    fun toSettings()

    /** G2/G3/G4 通知列表。 */
    fun toNotifications(category: NotificationCategory)

    /** H1 点点（推入式，无底部 Tab）。 */
    fun toAi()

    /** E2 发布表单页（媒体已在 [com.xiaohongshu.app.core.publish.PublishDraft] 中准备好）。 */
    fun toPublishForm()

    /** E3 写文字页。 */
    fun toWriteText()
}
