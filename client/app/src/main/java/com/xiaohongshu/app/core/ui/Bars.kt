package com.xiaohongshu.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
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
 * [unreadCount] 仅作用于「消息」栏，>0 时在文字右上角显示红底数字角标（>99 显示 99+）。
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
            )

            BottomTabItem(
                index = 1,
                label = "点点",
                selected = selectedIndex == 1,
                onSelect = onSelect,
                modifier = Modifier.weight(1f),
            )

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
                        .height(Dimens.plusButtonHeight)
                        .clip(RoundedCornerShape(Dimens.plusButtonRadius))
                        .background(if (guestMode) XhsColor.BtnGray else XhsColor.Red)
                        .clickableNoRipple(onClick = onPublish),
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
            )

            BottomTabItem(
                index = 4,
                label = "我",
                selected = selectedIndex == 4,
                onSelect = onSelect,
                modifier = Modifier.weight(1f),
            )
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

/**
 * 单个 Tab：文字水平垂直居中，整格可点（高 45 ≥ [Dimens.minTouchTarget]）。
 *
 * [badgeCount] > 0 时数字角标贴在**文字右上角**——原版此处是同一位置的 8×8 红点
 * （dump：点右缘超文字右缘 4dp、点下缘超文字上缘 4dp）；有具体未读数时用数字角标。
 */
@Composable
private fun BottomTabItem(
    index: Int,
    label: String,
    selected: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Long = 0,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.bottomBar)
            .clickableNoRipple { onSelect(index) },
        contentAlignment = Alignment.Center,
    ) {
        // 内层 Box 由文字撑开，角标才挂得上「文字右上角」而不是整格右上角
        Box {
            Text(
                text = label,
                style = if (selected) XhsType.bottomTabSelected else XhsType.bottomTabUnselected,
                color = if (selected) XhsColor.Text1 else XhsColor.Text2,
                maxLines = 1,
            )
            if (badgeCount > 0) {
                XhsCountBadge(
                    count = badgeCount,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-10).dp),
                )
            }
        }
    }
}

/**
 * 无涟漪点击。
 *
 * 原版底 Tab 与「＋」是瞬时切换/推入，没有 Material 水波纹；[Modifier.clickable] 默认取
 * `LocalIndication`（M3 = ripple），故显式把 indication 置空。
 */
@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)

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
