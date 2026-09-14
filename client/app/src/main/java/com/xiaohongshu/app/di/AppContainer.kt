package com.xiaohongshu.app.di

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xiaohongshu.app.BuildConfig
import com.xiaohongshu.app.core.auth.LoginGate
import com.xiaohongshu.app.core.image.ImageLoader
import com.xiaohongshu.app.core.interact.InteractionStore
import com.xiaohongshu.app.core.net.ApiClient
import com.xiaohongshu.app.core.net.SessionExpiryNotifier
import com.xiaohongshu.app.core.net.TokenHolder
import com.xiaohongshu.app.core.notify.UnreadCountCenter
import com.xiaohongshu.app.core.publish.PublishDraft
import com.xiaohongshu.app.core.ui.ToastController
import com.xiaohongshu.app.data.local.SearchHistoryStore
import com.xiaohongshu.app.data.local.SessionManager
import com.xiaohongshu.app.data.repo.AiRepository
import com.xiaohongshu.app.data.repo.CommentRepository
import com.xiaohongshu.app.data.repo.FollowRepository
import com.xiaohongshu.app.data.repo.NotificationRepository
import com.xiaohongshu.app.data.repo.PostRepository
import com.xiaohongshu.app.data.repo.UploadRepository
import com.xiaohongshu.app.data.repo.UserRepository

/**
 * 依赖容器（手工 DI）。
 *
 * 选型说明：本工程**不使用 Hilt**。Hilt 需要 KSP/kapt 与注解处理，会给 AGP 9 + Kotlin 2.2
 * 的构建引入额外版本耦合风险；而本项目的依赖图很浅（单容器、无多模块、无作用域需求），
 * 手工容器 + [appViewModel] 组合式取用更简单、可读，且启动期零反射开销。
 *
 * 生命周期：随 `Application` 创建，全程单例。所有成员都是惰性 `val`。
 */
class AppContainer(application: Application) {

    /** 全局 Toast（I2：屏幕中心 2.5s）。 */
    val toastController = ToastController()

    /** 打断 token 循环依赖：拦截器读它，SessionManager 写它。 */
    private val tokenHolder = TokenHolder()

    /** 登录态失效广播（I1）。 */
    val sessionExpiryNotifier = SessionExpiryNotifier()

    /** Retrofit 接口聚合。Base URL 来自 BuildConfig（可 -Pxhs.baseUrl= 覆盖）。 */
    private val apis = ApiClient.create(
        baseUrl = BuildConfig.XHS_BASE_URL,
        tokenProvider = tokenHolder,
        sessionExpiryNotifier = sessionExpiryNotifier,
        debugLogging = BuildConfig.DEBUG,
    )

    val sessionManager = SessionManager(application, apis.user, tokenHolder)

    /** 互动状态机（D1/D2）：乐观更新 + 回滚 + 跨页一致。 */
    val interactionStore = InteractionStore(
        likeApi = apis.like,
        collectApi = apis.collect,
        followApi = apis.follow,
        toasts = toastController,
    )

    val imageLoader = ImageLoader(application)

    val searchHistoryStore = SearchHistoryStore(application)

    val loginGate = LoginGate(sessionManager)

    /** 发布草稿（E1→E2→E3 交接）。 */
    val publishDraft = PublishDraft()

    // ---- Repository ----
    val postRepository = PostRepository(apis.post, apis.like, apis.collect)
    val userRepository = UserRepository(apis.user)
    val commentRepository = CommentRepository(apis.comment)
    val followRepository = FollowRepository(apis.follow)
    val notificationRepository = NotificationRepository(apis.notification)
    val aiRepository = AiRepository(apis.ai)
    val uploadRepository = UploadRepository(apis.upload)

    /** 未读数轮询（15s，仅前台 + 已登录）。 */
    val unreadCountCenter = UnreadCountCenter(notificationRepository, sessionManager)
}

/** 供 Compose 树读取容器。 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("LocalAppContainer 未提供：请在 XhsTheme 外层用 CompositionLocalProvider 注入")
}

/**
 * 取一个以 [AppContainer] 构造的 ViewModel。
 *
 * 用法：`val vm: HomeViewModel = appViewModel { HomeViewModel(it.postRepository) }`
 *
 * 比手写 `ViewModelProvider.Factory` 少一半样板，同时保留编译期类型安全
 * （构造函数参数写错立刻编译失败，而不是运行期崩溃）。
 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    noinline create: (AppContainer) -> VM,
): VM {
    val container = LocalAppContainer.current
    return viewModel(
        modelClass = VM::class.java,
        key = key,
        factory = viewModelFactory { initializer { create(container) } },
    )
}
