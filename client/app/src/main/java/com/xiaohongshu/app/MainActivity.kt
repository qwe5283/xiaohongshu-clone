package com.xiaohongshu.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.design.XhsTheme
import com.xiaohongshu.app.core.ui.LocalImageLoader
import com.xiaohongshu.app.core.ui.XhsToastHost
import com.xiaohongshu.app.di.LocalAppContainer
import com.xiaohongshu.app.navigation.AppNavHost

/**
 * 唯一 Activity（单 Activity + Compose 导航）。
 *
 * 采用 edge-to-edge：状态栏/手势条由各页面按需留白（顶栏 44dp 内含状态栏 inset，
 * 视频详情页 C2 则保持黑色沉浸、内容不侵入刘海与底栏）。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as XhsApplication).container

        setContent {
            XhsTheme {
                CompositionLocalProvider(
                    LocalAppContainer provides container,
                    LocalImageLoader provides container.imageLoader,
                    LocalRippleConfiguration provides null
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = XhsColor.Bg,
                    ) {
                        AppNavHost(container)
                    }
                    // Toast 挂在最上层，覆盖所有页面（屏幕中心，2.5s）
                    XhsToastHost(controller = container.toastController)
                }
            }
        }
    }
}
