package com.xiaohongshu.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffectResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.xiaohongshu.app.data.dto.NotificationCategory
import com.xiaohongshu.app.di.AppContainer
import com.xiaohongshu.app.feature.ai.AiRoute
import com.xiaohongshu.app.feature.auth.AuthRoute
import com.xiaohongshu.app.feature.detail.NoteDetailRoute
import com.xiaohongshu.app.feature.detail.VideoDetailRoute
import com.xiaohongshu.app.feature.message.ChatRoute
import com.xiaohongshu.app.feature.message.NotificationListRoute
import com.xiaohongshu.app.feature.profile.EditProfileRoute
import com.xiaohongshu.app.feature.profile.UserProfileRoute
import com.xiaohongshu.app.feature.publish.PublishFormRoute
import com.xiaohongshu.app.feature.publish.WriteTextRoute
import com.xiaohongshu.app.feature.search.SearchResultRoute
import com.xiaohongshu.app.feature.search.SearchRoute
import com.xiaohongshu.app.navigation.Transitions.popEnter
import com.xiaohongshu.app.navigation.Transitions.popExit
import com.xiaohongshu.app.navigation.Transitions.pushEnter
import com.xiaohongshu.app.navigation.Transitions.pushExit

/**
 * 全局导航图。
 *
 * 同时承担两项全局副作用（I1 与未读数通道规范）：
 * 1. 登录态失效 → 清登录态 + 清互动缓存 + 清角标 + 推入登录页（I1，区别于 A2 的「从未登录」）；
 * 2. 未读角标（WS 推送 + 关键时刻校正）与 WS 长连接，仅在「已登录 + App 前台」时运行。
 */
@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val navigator = remember(navController) { NavControllerNavigator(navController, container) }
    val session by container.sessionManager.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // ---- I1：会话过期全局处理 ----
    LaunchedEffect(container) {
        container.sessionExpiryNotifier.events.collect {
            container.sessionManager.onSessionExpired()
            container.interactionStore.clear()
            container.unreadCountCenter.clear()
            container.realtimeConnection.disconnect()
            // 已在 Auth 页时不重复推入
            val current = navController.currentDestination?.route
            if (current != Routes.AUTH) {
                navigator.toLogin()
            }
        }
    }

    // ---- 静默恢复登录态（I1：重启 APP 时本地有 Token 则校验恢复）----
    LaunchedEffect(container) {
        container.sessionManager.restore()
        container.unreadCountCenter.refresh()
    }

    // ---- 未读角标 + WS 实时通道生命周期：仅登录 + 前台 ----
    // 生命周期同未读数通道（契约 §12.7：连接维度 = 登录用户，非会话）
    DisposableEffect(lifecycleOwner, session.loggedIn, container) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START ->
                    if (session.loggedIn) {
                        container.unreadCountCenter.start(scope)
                        container.realtimeConnection.connect()
                    }
                Lifecycle.Event.ON_STOP -> {
                    container.unreadCountCenter.stop()
                    container.realtimeConnection.disconnect()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (session.loggedIn) {
            container.unreadCountCenter.start(scope)
            container.realtimeConnection.connect()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            container.unreadCountCenter.stop()
            container.realtimeConnection.disconnect()
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.MAIN,
        // 推入页统一「右进左出 + 旧页 1/4 视差」，
        // 返回自动反向。个别页面要换观感时，在该 composable 上覆盖这四个参数即可。
        enterTransition = { pushEnter() },
        exitTransition = { pushExit() },
        popEnterTransition = { popEnter() },
        popExitTransition = { popExit() },
    ) {
        // 根页（Tab 宿主）不参与横推：Tab 切换在 MainScaffold 内手写，不经过 NavHost；
        // 而所有「回首页」走的都是 popBackStack（自然拿 pop 过渡）。这里只钉住进场，
        // 防止将来有人改用 navigate(Routes.MAIN) 推入时根页从左侧滑回。
        composable(
            route = Routes.MAIN,
            enterTransition = { EnterTransition.None },
        ) { MainScaffold(navigator) }

        composable(Routes.AUTH) { AuthRoute(navigator) }

        composable(Routes.SEARCH) { SearchRoute(navigator) }
        composable(
            route = Routes.SEARCH_RESULT_PATTERN,
            arguments = listOf(
                navArgument(Routes.ARG_KEYWORD) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            SearchResultRoute(
                navigator = navigator,
                keyword = entry.arguments?.getString(Routes.ARG_KEYWORD).orEmpty(),
            )
        }

        composable(
            route = Routes.NOTE_DETAIL_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_POST_ID) { type = NavType.LongType }),
        ) { entry ->
            NoteDetailRoute(
                navigator = navigator,
                postId = entry.arguments?.getLong(Routes.ARG_POST_ID) ?: 0L,
            )
        }

        composable(
            route = Routes.VIDEO_DETAIL_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_POST_ID) { type = NavType.LongType }),
        ) { entry ->
            VideoDetailRoute(
                navigator = navigator,
                postId = entry.arguments?.getLong(Routes.ARG_POST_ID) ?: 0L,
            )
        }

        composable(Routes.PUBLISH_FORM) { PublishFormRoute(navigator) }
        composable(Routes.WRITE_TEXT) { WriteTextRoute(navigator) }

        composable(
            route = Routes.USER_PROFILE_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_USER_ID) { type = NavType.LongType }),
        ) { entry ->
            UserProfileRoute(
                navigator = navigator,
                userId = entry.arguments?.getLong(Routes.ARG_USER_ID) ?: 0L,
            )
        }

        composable(Routes.EDIT_PROFILE) { EditProfileRoute(navigator) }

        composable(
            route = Routes.NOTIFICATIONS_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_CATEGORY) { type = NavType.IntType }),
        ) { entry ->
            NotificationListRoute(
                navigator = navigator,
                category = NotificationCategory.entries
                    .firstOrNull { it.value == entry.arguments?.getInt(Routes.ARG_CATEGORY) }
                    ?: NotificationCategory.LIKE_COLLECT,
            )
        }

        composable(Routes.AI) { AiRoute(navigator) }

        composable(
            route = Routes.CHAT_PATTERN,
            arguments = listOf(
                navArgument(Routes.ARG_CONVERSATION_ID) { type = NavType.LongType },
                navArgument(Routes.ARG_PEER_ID) { type = NavType.LongType },
                navArgument(Routes.ARG_PEER_NICKNAME) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(Routes.ARG_PEER_AVATAR) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            ChatRoute(
                navigator = navigator,
                conversationId = entry.arguments?.getLong(Routes.ARG_CONVERSATION_ID) ?: 0L,
                peerId = entry.arguments?.getLong(Routes.ARG_PEER_ID) ?: 0L,
                peerNickname = entry.arguments?.getString(Routes.ARG_PEER_NICKNAME).orEmpty(),
                peerAvatar = entry.arguments?.getString(Routes.ARG_PEER_AVATAR).orEmpty(),
            )
        }
    }
}

/**
 * [AppNavigator] 的 NavController 实现。集中在一处，路由表变更不影响各 feature。
 */
private class NavControllerNavigator(
    private val navController: NavController,
    private val container: AppContainer,
) : AppNavigator {

    override fun back() {
        navController.popBackStack()
    }

    /**
     * 统一推入出口，双重防连点（修复转场 300ms 窗口内连点导致重复压栈）：
     * 1. RESUMED 守卫：推入转场进行时新栈顶被压在 STARTED、要等动画播完才 RESUMED，
     *    而旧页此刻仍在组合中可被点中——窗口期点击一律丢弃（返回转场不受影响，露出的页面立即 RESUMED）；
     * 2. launchSingleTop 兜底：目标与栈顶同目的地时不重复压栈。注意它按路由模板匹配，
     *    同路由不同参数会替换参数而非压栈——目前不存在此类链路，将来要加「笔记 A → 笔记 B」
     *    这类同路由连环跳时，需为该入口改回普通 navigate。
     */
    private fun navigateChecked(route: String) {
        val current = navController.currentBackStackEntry ?: return
        if (current.lifecycle.currentState != Lifecycle.State.RESUMED) return
        navController.navigate(route) { launchSingleTop = true }
    }

    override fun toMain() {
        navController.popBackStack(Routes.MAIN, inclusive = false)
    }

    override fun toLogin() {
        // 不走 navigateChecked：I1 的 401 重定向可能在转场窗口内到达，被守卫吞掉会把用户
        // 困在已清空会话的页面上；连点重复推入由 launchSingleTop 兜住（目标恒为 AUTH）。
        navController.navigate(Routes.AUTH) { launchSingleTop = true }
    }

    override fun popLogin() {
        container.loginGate.consumePending()
        navController.popBackStack()
    }

    override fun toSearch() {
        navigateChecked(Routes.SEARCH)
    }

    override fun toSearchResult(keyword: String) {
        navigateChecked(Routes.searchResult(keyword))
    }

    override fun toNoteDetail(postId: Long) {
        navigateChecked(Routes.noteDetail(postId))
    }

    override fun toNote(postId: Long, isVideo: Boolean) {
        if (isVideo) toVideoDetail(postId) else toNoteDetail(postId)
    }

    override fun toVideoDetail(postId: Long) {
        navigateChecked(Routes.videoDetail(postId))
    }

    override fun toUserProfile(userId: Long) {
        navigateChecked(Routes.userProfile(userId))
    }

    override fun toEditProfile() {
        navigateChecked(Routes.EDIT_PROFILE)
    }

    override fun toNotifications(category: NotificationCategory) {
        navigateChecked(Routes.notifications(category.value))
    }

    override fun toAi() {
        navigateChecked(Routes.AI)
    }

    override fun toChat(conversationId: Long, peerId: Long, peerNickname: String, peerAvatar: String) {
        navigateChecked(Routes.chat(conversationId, peerId, peerNickname, peerAvatar))
    }

    override fun toPublishForm() {
        navigateChecked(Routes.PUBLISH_FORM)
    }

    override fun toWriteText() {
        navigateChecked(Routes.WRITE_TEXT)
    }
}
