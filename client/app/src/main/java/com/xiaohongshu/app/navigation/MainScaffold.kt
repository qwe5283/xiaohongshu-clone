package com.xiaohongshu.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaohongshu.app.core.ui.XhsBottomTabBar
import com.xiaohongshu.app.di.LocalAppContainer
import com.xiaohongshu.app.feature.home.HomeRoute
import com.xiaohongshu.app.feature.message.MessageRoute
import com.xiaohongshu.app.feature.profile.MyProfileRoute
import com.xiaohongshu.app.feature.publish.PublishEntrySheet

/** 底 Tab 索引（线框固定五栏：首页 / 点点 / ＋ / 消息 / 我）。 */
object Tabs {
    const val HOME = 0
    const val AI = 1
    const val PUBLISH = 2
    const val MESSAGE = 3
    const val ME = 4
}

/**
 * Tab 宿主（B1 / G1 / F1）。
 *
 * 只有首页、消息、我三栏带底部 Tab 栏；「点点」按线框是**推入式页面**（Tab 仅为入口，无底部 Tab），
 * 因此点击它直接 `navigate(AI)` 而不改变选中项——返回时自然回到原 Tab。
 *
 * 游客拦截（A1/A2）：点「消息」「我」「＋」以及首页卡片♡等写操作 → 直接推入登录页，**不弹 Toast**。
 */
@Composable
fun MainScaffold(navigator: AppNavigator) {
    val container = LocalAppContainer.current
    val session by container.sessionManager.state.collectAsStateWithLifecycle()
    val unread by container.unreadCountCenter.counts.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(Tabs.HOME) }
    var showPublishSheet by remember { mutableStateOf(false) }

    // F6 退出登录 → 回游客首页（A1，带登录悬浮条）。
    // 若停留在「我」Tab，退出后会看到 F1 的游客兜底页而不是首页，与线框的落点不符。
    LaunchedEffect(session.loggedIn) {
        if (!session.loggedIn) {
            selectedTab = Tabs.HOME
            showPublishSheet = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                Tabs.HOME -> HomeRoute(navigator)
                Tabs.MESSAGE -> MessageRoute(navigator)
                else -> MyProfileRoute(navigator)
            }
        }

        XhsBottomTabBar(
            selectedIndex = selectedTab,
            unreadCount = unread.total,
            onSelect = { index ->
                when (index) {
                    // 点点：推入式页面，Tab 不作为选中态。
                    // 仅登录用户可达：游客点「点点」Tab → A2 推入登录页（线框 H1）。
                    Tabs.AI ->
                        if (session.loggedIn) navigator.toAi() else navigator.toLogin()

                    // 游客点「消息」「我」→ A2 推入登录页
                    Tabs.MESSAGE, Tabs.ME ->
                        if (session.loggedIn) selectedTab = index else navigator.toLogin()

                    else -> selectedTab = index
                }
            },
            onPublish = {
                if (session.loggedIn) showPublishSheet = true else navigator.toLogin()
            },
            // A1 游客态：＋ 为灰块、无消息角标
            // guestMode = !session.loggedIn,
        )
    }

    // E1 发布入口弹层（相册 / 拍摄 / 写文字），内部处理权限与选图
    PublishEntrySheet(
        visible = showPublishSheet,
        onDismiss = { showPublishSheet = false },
        navigator = navigator,
    )
}
