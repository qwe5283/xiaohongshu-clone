package com.xiaohongshu.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaohongshu.app.R
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.design.XhsType

/**
 * 页面顶栏（线框 T-4c：高 44，图标 24，标题居中）。
 *
 * 默认包含状态栏内边距，因此页面只要把它放在最顶部即可，无需再处理 inset。
 * 沉浸式视频页（C2）请传 [transparent] = true 并把 [contentColor] 置白。
 */
@Composable
fun XhsTopBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    contentColor: Color = XhsColor.Text1,
    backgroundColor: Color = XhsColor.Bg,
    transparent: Boolean = false,
    showDivider: Boolean = false,
    height: Dp = Dimens.topBar,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    /** 中部自定义内容（优先于 [title]）：用于「关注/发现」页签等非纯文本标题。 */
    center: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(if (transparent) Color.Transparent else backgroundColor),
    ) {
        Spacer(modifier = Modifier.statusBarsPadding())
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            // 左：返回 / 自定义
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    navigationIcon != null -> navigationIcon()
                    onBack != null -> XhsIconButton(
                        iconRes = R.drawable.ic_chevron_left,
                        onClick = onBack,
                        tint = contentColor,
                        contentDescription = "返回",
                    )
                }
            }

            // 中：自定义内容优先，其次标题
            if (center != null) {
                Box(modifier = Modifier.align(Alignment.Center)) { center() }
            } else if (title != null) {
                Text(
                    text = title,
                    style = XhsType.pageTitle,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 64.dp),
                )
            }

            // 右：动作
            if (actions != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { actions() }
            }
        }
        if (showDivider) {
            XhsDivider()
        }
    }
}

/**
 * 底部 Tab 栏（线框 T-4c：高 48，5 等分；＋钮 56×48 圆角 12 满高红块）。
 *
 * 固定五栏：首页 / 点点 / ＋ / 消息 / 我（本复刻以「点点」替换原版「市集」，见章节 H）。
 * [selectedIndex] 取值 0（首页）、1（点点）、3（消息）、4（我）；2 为 ＋，不是选中态。
 * [unreadCount] 仅作用于「消息」栏，>0 时显示红底数字角标（>99 显示 99+）。
 * [guestMode] = true 时（A1 游客态）＋ 按钮渲染为灰块而非品牌红。
 */
@Composable
fun XhsBottomTabBar(
    selectedIndex: Int,
    unreadCount: Long,
    onSelect: (Int) -> Unit,
    onPublish: () -> Unit,
    modifier: Modifier = Modifier,
    guestMode: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        XhsDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.bottomBar)
                .background(XhsColor.Bg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomTabItem(
                index = 0,
                label = "首页",
                selected = selectedIndex == 0,
                onSelect = onSelect,
                modifier = Modifier.weight(1f),
            ) { selected ->
                Icon(
                    painter = painterResource(R.drawable.ic_home),
                    contentDescription = null,
                    tint = if (selected) XhsColor.Text1 else XhsColor.Text2,
                    modifier = Modifier.size(Dimens.icon24),
                )
            }

            BottomTabItem(
                index = 1,
                label = "点点",
                selected = selectedIndex == 1,
                onSelect = onSelect,
                modifier = Modifier.weight(1f),
            ) { selected ->
                Icon(
                    painter = painterResource(R.drawable.ic_assistant),
                    contentDescription = null,
                    tint = if (selected) XhsColor.Text1 else XhsColor.Text2,
                    modifier = Modifier.size(Dimens.icon24),
                )
            }

            // ＋ 发布：满高圆角红块，不是「选中态」
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(Dimens.plusButtonWidth)
                        .height(Dimens.bottomBar)
                        .clip(RoundedCornerShape(Dimens.plusButtonRadius))
                        .background(if (guestMode) XhsColor.BtnGray else XhsColor.Red)
                        .clickable(onClick = onPublish),
                    contentAlignment = Alignment.Center,
                ) {
                    XhsPlusGlyph(
                        size = 22.dp,
                        color = if (guestMode) XhsColor.Text2 else Color.White,
                    )
                }
            }

            BottomTabItem(
                index = 3,
                label = "消息",
                selected = selectedIndex == 3,
                onSelect = onSelect,
                badgeCount = unreadCount,
                modifier = Modifier.weight(1f),
            ) { selected ->
                Icon(
                    painter = painterResource(R.drawable.ic_notify),
                    contentDescription = null,
                    tint = if (selected) XhsColor.Text1 else XhsColor.Text2,
                    modifier = Modifier.size(Dimens.icon24),
                )
            }

            BottomTabItem(
                index = 4,
                label = "我",
                selected = selectedIndex == 4,
                onSelect = onSelect,
                modifier = Modifier.weight(1f),
            ) { selected ->
                XhsPersonGlyph(
                    size = Dimens.icon24,
                    color = if (selected) XhsColor.Text1 else XhsColor.Text2,
                    filled = selected,
                )
            }
        }
        // 底部手势条留白（edge-to-edge 下避免被系统手势条遮挡）
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .background(XhsColor.Bg)
                .navigationBarsPadding(),
        )
    }
}

@Composable
private fun BottomTabItem(
    index: Int,
    label: String,
    selected: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Long = 0,
    icon: @Composable (Boolean) -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.bottomBar)
                .clickable { onSelect(index) },
        ) {
            Box(contentAlignment = Alignment.Center) {
                icon(selected)
                if (badgeCount > 0) {
                    XhsCountBadge(
                        count = badgeCount,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 10.dp, y = (-6).dp),
                    )
                }
            }
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    style = if (selected) XhsType.s(17, emphasis = true) else XhsType.s(15),
                    color = if (selected) XhsColor.Text1 else XhsColor.Text2,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 缩放下箭头：复用 chevron-left 旋转 90°（B3-1 筛选「全部▾」、E1 展开类控件）。 */
@Composable
fun XhsChevronDown(
    modifier: Modifier = Modifier,
    size: Dp = Dimens.icon16,
    tint: Color = XhsColor.Text1,
) {
    Icon(
        painter = painterResource(R.drawable.ic_chevron_left),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size).rotate(-90f),
    )
}

/** 右箭头（列表行进入）。 */
@Composable
fun XhsChevronRight(
    modifier: Modifier = Modifier,
    size: Dp = Dimens.icon16,
    tint: Color = XhsColor.Text3,
) {
    Icon(
        painter = painterResource(R.drawable.ic_chevron_right),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size),
    )
}
