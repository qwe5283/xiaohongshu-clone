package com.xiaohongshu.app.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.xiaohongshu.app.R
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.design.XhsType
import com.xiaohongshu.app.core.ui.XhsDivider
import com.xiaohongshu.app.core.ui.XhsScrim
import com.xiaohongshu.app.domain.model.User

/** 规范 §7 F4：由 MainScaffold 挂载，抽屉遮罩才能覆盖底部 Tab。 */
@Composable
internal fun ProfileDrawer(
    visible: Boolean,
    user: User,
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = visible) { onDismiss() }
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
        ) {
            XhsScrim(onClick = onDismiss)
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(tween(220)) { -it },
            exit = slideOutHorizontally(tween(180)) { -it },
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Column(
                modifier = Modifier
                    .width(Dimens.drawerWidth)
                    .fillMaxHeight()
                    .background(XhsColor.Bg),
            ) {
                Spacer(modifier = Modifier.statusBarsPadding())

                Text(
                    text = user.displayName,
                    style = XhsType.pageTitle,
                    color = XhsColor.Text1,
                    modifier = Modifier.padding(
                        start = Dimens.pagePadding,
                        end = Dimens.pagePadding,
                        top = Dimens.s16,
                        bottom = Dimens.s16,
                    ),
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.s12),
                ) {
                    DrawerItem(label = "设置", iconRes = R.drawable.ic_settings, onClick = onSettings)
                    XhsDivider()
                    DrawerItem(label = "社区公约", iconRes = R.drawable.ic_community_rules, onClick = {})
                    XhsDivider()
                    DrawerItem(label = "关于我们", iconRes = R.drawable.ic_about, onClick = {})
                }

                Spacer(modifier = Modifier.height(Dimens.drawerGroupGap))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.s12),
                ) {
                    DrawerItem(label = "退出登录", iconRes = R.drawable.ic_logout, onClick = onLogout, emphasized = true)
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.s12)
                        .height(Dimens.drawerGridCell),
                ) {
                    DrawerGridIcons.forEach { iconRes ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(Dimens.drawerGridCell),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = null,
                                tint = XhsColor.Text3,
                                modifier = Modifier.size(Dimens.icon24),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }
    }
}

// 交付说明 §8：三格用途尚未定义，仅展示候选字形，不启用入口。
private val DrawerGridIcons = listOf(R.drawable.ic_moon, R.drawable.ic_qr_code, R.drawable.ic_feedback)

@Composable
private fun DrawerItem(
    label: String,
    onClick: () -> Unit,
    iconRes: Int,
    emphasized: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.rowDrawer)
            .clickable(onClick = onClick)
            .padding(start = Dimens.s16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = XhsColor.Text2,
            modifier = Modifier.size(Dimens.icon24),
        )
        Spacer(modifier = Modifier.width(Dimens.s12))
        Text(
            text = label,
            style = XhsType.s(15, emphasis = emphasized),
            color = XhsColor.Text1,
            maxLines = 1,
        )
    }
}
