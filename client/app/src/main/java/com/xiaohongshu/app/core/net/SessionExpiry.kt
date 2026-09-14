package com.xiaohongshu.app.core.net

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** 提供当前登录 token；由 [TokenHolder] 实现（避免 core 依赖 data 层）。 */
fun interface TokenProvider {
    fun token(): String?
}

/**
 * 可写的 token 持有者。
 *
 * 存在的理由：`ApiClient` 需要 token 才能构造，而 `SessionManager` 又需要 `UserApi`，
 * 直接互相依赖会成环。用一个可写的 holder 打断这个环——拦截器读 holder，
 * SessionManager 在登录/登出时写 holder。
 */
class TokenHolder : TokenProvider {
    @Volatile
    private var value: String? = null

    override fun token(): String? = value

    fun set(token: String?) {
        value = token
    }
}

/**
 * 登录态失效广播。
 *
 * 触发源有两处：
 * 1. [SessionExpiryInterceptor] 发现 HTTP 401；
 * 2. 信封 `code ∈ {1005,1006,1007}`（HTTP 200/400 的情况）。
 *
 * 消费方（MainActivity / AppNavHost）收到后：清登录态 → 推入登录页（A3/I1）。
 * 用 [MutableSharedFlow] 的 replay=0 + extraBufferCapacity 保证不阻塞网络线程。
 */
class SessionExpiryNotifier {

    private val _events = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = _events

    fun notifyExpired() {
        _events.tryEmit(Unit)
    }
}
