package com.xiaohongshu.app.feature.auth

/**
 * A4 → A3 的用户名回填交接（线框 A4：「注册成功 → Toast「注册成功，请登录」→ 回 A3 并预填用户名」）。
 *
 * 为什么用包内单例，而不是导航参数 / `previousBackStackEntry.savedStateHandle`：
 * - 入口签名固定为 `LoginRoute(navigator)` / `RegisterRoute(navigator)`，feature 只依赖
 *   [com.xiaohongshu.app.navigation.AppNavigator]，拿不到 `NavController` 的
 *   `previousBackStackEntry`；
 * - `navigation/` 与 `Routes` 不属于本工作流（所有权表 §5），不能加参数。
 *
 * 生命周期：注册成功时写入一次，A3 重新进入时 [consumeUsername] 读取并**立即清空**，
 * 因此不会残留到下一次进入登录页；进程内单例与「一次注册 → 一次回填」一一对应。
 */
internal object AuthPrefill {

    private var username: String? = null

    /** 注册成功时写入待回填的用户名。 */
    fun setUsername(value: String) {
        username = value
    }

    /** A3 进入时取走回填值（取出即清空，返回 null 表示本次无需回填）。 */
    fun consumeUsername(): String? {
        val value = username
        username = null
        return value
    }
}
