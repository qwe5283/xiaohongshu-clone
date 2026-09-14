package com.xiaohongshu.app.navigation

/**
 * 路由表。字符串路由（配合 navigation-compose），参数用花括号占位。
 * 屏号对应 client/docs/低保真线框图-移动端-v6.html 的屏幕编号。
 */
object Routes {

    /** Tab 宿主（首页 / 点点入口 / ＋ / 消息 / 我）。 */
    const val MAIN = "main"

    /** A3 登录页（推入式全屏）。 */
    const val LOGIN = "login"

    /** A4 注册页。 */
    const val REGISTER = "register"

    /** B2 搜索页。 */
    const val SEARCH = "search"

    /** B3-1/B3-2 搜索结果页。 */
    const val SEARCH_RESULT = "search/result"

    /** C1-1 图文笔记详情。 */
    const val NOTE_DETAIL = "note"

    /** C2-1 视频笔记详情（深色沉浸）。 */
    const val VIDEO_DETAIL = "video"

    /** E2 发布表单页。 */
    const val PUBLISH_FORM = "publish/form"

    /** E3 写文字页。 */
    const val WRITE_TEXT = "publish/text"

    /** F2 他人主页。 */
    const val USER_PROFILE = "user"

    /** F3 编辑资料页。 */
    const val EDIT_PROFILE = "profile/edit"

    /** F5 设置页。 */
    const val SETTINGS = "settings"

    /** G2/G3/G4 通知列表（按入口分类）。 */
    const val NOTIFICATIONS = "notifications"

    /** H1/H2 点点对话页（推入式，无底部 Tab）。 */
    const val AI = "ai"

    // ---- 参数名 ----
    const val ARG_POST_ID = "postId"
    const val ARG_USER_ID = "userId"
    const val ARG_KEYWORD = "keyword"
    const val ARG_CATEGORY = "category"

    // ---- 带参路由构造 ----
    fun searchResult(keyword: String): String =
        "$SEARCH_RESULT?$ARG_KEYWORD=${java.net.URLEncoder.encode(keyword, "UTF-8")}"

    fun noteDetail(postId: Long): String = "$NOTE_DETAIL/$postId"

    fun videoDetail(postId: Long): String = "$VIDEO_DETAIL/$postId"

    fun userProfile(userId: Long): String = "$USER_PROFILE/$userId"

    fun notifications(category: Int): String = "$NOTIFICATIONS/$category"

    // ---- 路由模式（用于 NavHost 声明）----
    const val NOTE_DETAIL_PATTERN = "$NOTE_DETAIL/{$ARG_POST_ID}"
    const val VIDEO_DETAIL_PATTERN = "$VIDEO_DETAIL/{$ARG_POST_ID}"
    const val USER_PROFILE_PATTERN = "$USER_PROFILE/{$ARG_USER_ID}"
    const val NOTIFICATIONS_PATTERN = "$NOTIFICATIONS/{$ARG_CATEGORY}"
    const val SEARCH_RESULT_PATTERN = "$SEARCH_RESULT?$ARG_KEYWORD={$ARG_KEYWORD}"
}
