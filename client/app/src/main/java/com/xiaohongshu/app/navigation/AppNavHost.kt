package com.xiaohongshu.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffectResult
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import com.xiaohongshu.app.feature.auth.LoginRoute
import com.xiaohongshu.app.feature.auth.RegisterRoute
import com.xiaohongshu.app.feature.detail.NoteDetailRoute
import com.xiaohongshu.app.feature.detail.VideoDetailRoute
import com.xiaohongshu.app.feature.message.NotificationListRoute
import com.xiaohongshu.app.feature.profile.EditProfileRoute
import com.xiaohongshu.app.feature.profile.SettingsRoute
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
 * 同时承担两项全局副作用（线框 I1 与会话/轮询规范）：
 * 1. 登录态失效 → 清登录态 + 清互动缓存 + 清角标 + 推入登录页（I1，区别于 A2 的「从未登录」）；
 * 2. 未读角标 15s 轮询，仅在「已登录 + App 前台」时运行。
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
            // 已在登录页时不重复推入
            val current = navController.currentDestination?.route
            if (current != Routes.LOGIN && current != Routes.REGISTER) {
                navigator.toLogin()
            }
        }
    }

    // ---- 静默恢复登录态（I1：重启 APP 时本地有 Token 则校验恢复）----
    LaunchedEffect(container) {
        container.sessionManager.restore()
        container.unreadCountCenter.refresh()
    }

    // ---- 未读角标轮询：仅登录 + 前台 ----
    DisposableEffect(lifecycleOwner, session.loggedIn, container) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START ->
                    if (session.loggedIn) container.unreadCountCenter.start(scope)
                Lifecycle.Event.ON_STOP -> container.unreadCountCenter.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (session.loggedIn) container.unreadCountCenter.start(scope)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            container.unreadCountCenter.stop()
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.MAIN,
        // 全部推入页（登录/注册/搜索/详情/发布/主页/设置/通知/点点）统一「右进左出 + 旧页 1/4 视差」，
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

        composable(Routes.LOGIN) { LoginRoute(navigator) }
        composable(Routes.REGISTER) { RegisterRoute(navigator) }

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
        composable(Routes.SETTINGS) { SettingsRoute(navigator) }

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

    override fun toMain() {
        navController.popBackStack(Routes.MAIN, inclusive = false)
    }

    override fun toLogin() {
        navController.navigate(Routes.LOGIN)
    }

    override fun toRegister() {
        navController.navigate(Routes.REGISTER)
    }

    override fun popLogin() {
        container.loginGate.consumePending()
        navController.popBackStack()
    }

    override fun toSearch() {
        navController.navigate(Routes.SEARCH)
    }

    override fun toSearchResult(keyword: String) {
        navController.navigate(Routes.searchResult(keyword))
    }

    override fun toNoteDetail(postId: Long) {
        navController.navigate(Routes.noteDetail(postId))
    }

    override fun toNote(postId: Long, isVideo: Boolean) {
        if (isVideo) toVideoDetail(postId) else toNoteDetail(postId)
    }

    override fun toVideoDetail(postId: Long) {
        navController.navigate(Routes.videoDetail(postId))
    }

    override fun toUserProfile(userId: Long) {
        navController.navigate(Routes.userProfile(userId))
    }

    override fun toEditProfile() {
        navController.navigate(Routes.EDIT_PROFILE)
    }

    override fun toSettings() {
        navController.navigate(Routes.SETTINGS)
    }

    override fun toNotifications(category: NotificationCategory) {
        navController.navigate(Routes.notifications(category.value))
    }

    override fun toAi() {
        navController.navigate(Routes.AI)
    }

    override fun toPublishForm() {
        navController.navigate(Routes.PUBLISH_FORM)
    }

    override fun toWriteText() {
        navController.navigate(Routes.WRITE_TEXT)
    }
}
