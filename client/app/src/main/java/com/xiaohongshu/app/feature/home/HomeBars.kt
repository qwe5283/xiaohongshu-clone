package com.xiaohongshu.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaohongshu.app.R
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.design.XhsType
import com.xiaohongshu.app.core.ui.XhsChevronRight
import com.xiaohongshu.app.core.ui.XhsDivider
import com.xiaohongshu.app.core.ui.XhsIconButton

/**
 * B1/A1 首页顶栏：高 44（含状态栏 inset）。
 *
 * - 左：登录态＝点点气泡（→ H1）；游客＝☰（→ 登录页，A1 note：游客无抽屉）；
 * - 中：「关注 / 发现」两页签 20sp，选中加粗 + 2dp 下划线（线框 B1 navtop）；
 * - 右：搜索图标（→ B2）。
 */
@Composable
internal fun HomeTopBar(
    loggedIn: Boolean,
    tab: HomeTab,
    onTabSelect: (HomeTab) -> Unit,
    onLeftAction: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(XhsColor.Bg),
    ) {
        Spacer(modifier = Modifier.statusBarsPadding())
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.topBar),
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = Dimens.s4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (loggedIn) {
                    XhsIconButton(
                        iconRes = R.drawable.ic_assistant,
                        onClick = onLeftAction,
                        contentDescription = "点点",
                    )
                } else {
                    MenuEntryButton(onClick = onLeftAction)
                }
            }

            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.s24),
            ) {
                UnderlineLabel(
                    text = "关注",
                    textStyle = if (tab == HomeTab.FOLLOWING) XhsType.topBarTabSelected else XhsType.topBarTabUnselected,
                    contentColor = if (tab == HomeTab.FOLLOWING) XhsColor.Text1 else XhsColor.Text2,
                    underlineColor = if (tab == HomeTab.FOLLOWING) XhsColor.Red else Color.Transparent,
                    onClick = { onTabSelect(HomeTab.FOLLOWING) },
                )
                UnderlineLabel(
                    text = "发现",
                    textStyle = if (tab == HomeTab.DISCOVER) XhsType.topBarTabSelected else XhsType.topBarTabUnselected,
                    contentColor = if (tab == HomeTab.DISCOVER) XhsColor.Text1 else XhsColor.Text2,
                    underlineColor = if (tab == HomeTab.DISCOVER) XhsColor.Red else Color.Transparent,
                    onClick = { onTabSelect(HomeTab.DISCOVER) },
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = Dimens.s4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                XhsIconButton(
                    iconRes = R.drawable.ic_search,
                    onClick = onSearchClick,
                    contentDescription = "搜索",
                )
            }
        }
        XhsDivider()
    }
}

/** 游客态左上 ☰：点击推入登录页（A1）。 */
@Composable
private fun MenuEntryButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = Dimens.minTouchTarget, minHeight = Dimens.minTouchTarget)
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_menu),
            contentDescription = "菜单",
            tint = XhsColor.Text1,
            modifier = Modifier.size(Dimens.icon24),
        )
    }
}

/**
 * B1 频道栏：高 40，横向滚动，点击仅高亮（线框注 #3）。
 * 右端固定 60dp 渐隐 + 16dp 箭头，暗示「横向滑动查看更多」。
 */
@Composable
internal fun ChannelBar(
    channels: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.channelBar),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .horizontalScroll(rememberScrollState())
                // 右侧多留出渐隐宽度，保证可横滑到底时最后一项不被渐隐/箭头盖住
                .padding(start = Dimens.pagePadding, end = Dimens.channelFadeWidth),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.s12),
        ) {
            channels.forEach { channel ->
                val isSelected = channel == selected
                UnderlineLabel(
                    text = channel,
                    textStyle = if (isSelected) XhsType.tabSelected else XhsType.tabUnselected,
                    contentColor = if (isSelected) XhsColor.Text1 else XhsColor.Text2,
                    underlineColor = Color.Transparent,
                    onClick = { onSelect(channel) },
                )
            }
        }

        // 右渐隐（60）+ 箭头（16）
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(Dimens.channelFadeWidth)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        listOf(XhsColor.Bg.copy(alpha = 0f), XhsColor.Bg),
                    ),
                ),
            contentAlignment = Alignment.CenterEnd,
        ) {
            XhsChevronRight(
                size = Dimens.channelFadeArrow,
                tint = XhsColor.Text1,
                modifier = Modifier.padding(end = Dimens.s4),
            )
        }
    }
}

/**
 * A1 底部登录悬浮条：「🔒 登录后体验更多功能 [登录]」。
 *
 * 悬浮在瀑布流之上、底 Tab 之上（线框 `.floatbar`：left/right 8、bottom 44、圆角 14、深底白字）。
 * 🔒 与「登录」同款描边 pill 均取自线框原文（线框本身用字符表示该图标，故按文本渲染）。
 */
@Composable
internal fun GuestLoginBar(
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(HomeGuestBarHeight)
            .clip(RoundedCornerShape(Dimens.s8))
            .background(XhsColor.Dark)
            // 整条可点：既是「登录」入口，也避免点空白处误触到下方卡片
            .clickable(onClick = onLogin)
            .padding(start = Dimens.s12, end = Dimens.s8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.s8),
    ) {
        Text(
            text = "🔒 登录后体验更多功能",
            style = XhsType.s(14),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .height(Dimens.buttonFollow)
                .clip(RoundedCornerShape(Dimens.radiusPill))
                .clickable(onClick = onLogin)
                .padding(horizontal = Dimens.s12),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "去登录", style = XhsType.body, color = Color.Red)
        }
    }
}

/**
 * 「文本 + 2dp 下划线」的页签/频道标签，B1 顶栏页签与频道栏共用。
 *
 * 下划线用 `drawBehind` 画在文字自身尺寸之下（宽度＝文字实际宽度，无需再测量），
 * 外层 Box 补足 ≥44dp 触控热区。
 *
 * 想隐藏下划线时为 `underlineColor` 传入透明值。
 */
@Composable
private fun UnderlineLabel(
    text: String,
    textStyle: TextStyle,
    contentColor: Color,
    underlineColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .defaultMinSize(minWidth = Dimens.minTouchTarget, minHeight = Dimens.minTouchTarget)
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = textStyle,
            color = contentColor,
            maxLines = 1,
            modifier = Modifier.drawBehind {
                val underlineHeight = TabUnderlineHeight.toPx()
                val underlineWidth = Dimens.topBarTabUnderlineWidth.toPx()
                drawRoundRect(
                    color = underlineColor,
                    topLeft = Offset(
                        x = (size.width - underlineWidth) / 2f,
                        y = size.height + TabUnderlineGap.toPx()
                    ),
                    size = Size(width = underlineWidth, height = underlineHeight),
                    cornerRadius = CornerRadius(underlineHeight / 2f) // 高度一半 = 完美体育场形
                )
            },
        )
    }
}

/** A1 悬浮条高度：线框 `.floatbar`（5px 内边距 + 9.5px 文字）归一化后约 40dp，Dimens 无对应档位。 */
internal val HomeGuestBarHeight = 40.dp

/** 悬浮条在列表底部需要预留的高度：条高 + 上下各 8 的悬浮间距。 */
internal val HomeGuestBarReserved = HomeGuestBarHeight + Dimens.s16

private val TabUnderlineHeight = 2.dp
private val TabUnderlineGap = 4.dp
