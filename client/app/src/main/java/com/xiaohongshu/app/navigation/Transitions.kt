package com.xiaohongshu.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/**
 * 推入式页面的转场：新页自右向左滑入，旧页左移 1/4 幅做视差；返回时整体反向。
 *
 * 挂在 [androidx.navigation.compose.NavHost] 的四个默认参数上，即可一次覆盖全部推入页（见 [AppNavHost]）。
 * 根页 [Routes.MAIN] 例外——那里显式钉成无进场动画。
 *
 * 为什么必须是「扩展函数」而不是普通函数：`composable(...)` 的转场参数类型是
 * `AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?`，位移量只有在这个
 * scope 内才知道容器尺寸，所以只能以它为 receiver；调用点还得再包一层 lambda
 * （`enterTransition = { pushEnter() }`），让 receiver 被推断为 `NavBackStackEntry`。
 */
object Transitions {

    /** 单程时长 300ms。页面推入比弹层/抽屉的 220ms 更从容，两者是两套节奏，不要互相靠拢。 */
    const val DURATION_MS = 300

    /** 退场/回场的位移比例：只走全幅的 1/4，保留前后层次；取 1f 就是旧页整屏滑出。 */
    private const val PARALLAX = 0.25f

    private val spec: FiniteAnimationSpec<IntOffset> =
        tween(DURATION_MS, easing = FastOutSlowInEasing)

    /** 推入：新页从右侧进场。 */
    fun AnimatedContentTransitionScope<*>.pushEnter(): EnterTransition =
        slideIntoContainer(SlideDirection.Left, spec)

    /** 推入：旧页向左退 1/4 幅（不做整屏滑出，避免两页同时大幅位移的廉价感）。 */
    fun AnimatedContentTransitionScope<*>.pushExit(): ExitTransition =
        slideOutOfContainer(SlideDirection.Left, spec, targetOffset = { (it * PARALLAX).toInt() })

    /** 返回：被盖住的页面从左侧回到原位。 */
    fun AnimatedContentTransitionScope<*>.popEnter(): EnterTransition =
        slideIntoContainer(SlideDirection.Right, spec, initialOffset = { (it * PARALLAX).toInt() })

    /** 返回：当前页向右滑出。 */
    fun AnimatedContentTransitionScope<*>.popExit(): ExitTransition =
        slideOutOfContainer(SlideDirection.Right, spec)
}
