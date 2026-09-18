package com.xiaohongshu.app.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.design.XhsType

/**
 * 主操作按钮（A3/A4 登录注册、E2 发布、B4-2 重试）。
 * 全圆角胶囊、黑底白字（线框 .btn.rd）；加载中显示「登录中...」并禁用（A5-2）。
 */
@Composable
fun XhsPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingText: String? = null,
    height: Dp = 44.dp,
    containerColor: Color = XhsColor.Text1,
    contentColor: Color = Color.White,
    /** false 时不撑满宽度（B2 的「搜索」按钮 56×44 用）。 */
    fillWidth: Boolean = true,
) {
    val active = enabled && !loading
    Box(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .height(height)
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .background(if (active) containerColor else containerColor.copy(alpha = 0.45f))
            .clickable(enabled = active, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (loading) (loadingText ?: text) else text,
            style = XhsType.buttonLabel,
            color = contentColor,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 关注按钮状态机（D2）。
 *
 * - 未关注：品牌红实心 pill，文字「关注」；
 * - 已关注：灰底（[XhsColor.BtnGray]）文字「已关注」，点击触发取关分支。
 *
 * 详情作者栏用 28dp 高小胶囊（[XhsFollowPill]）；他人主页用通栏大按钮（[XhsFollowWideButton]）。
 * **自己的笔记/主页不显示关注按钮**，由调用方判断。
 */
@Composable
fun XhsFollowPill(
    followed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = Dimens.buttonFollow,
    minWidth: Dp = 64.dp,
    followFg: Color = Color.White,
    followBg: Color = XhsColor.Red,
    followedFg: Color = XhsColor.BtnGrayText,
    followedBg: Color = XhsColor.BtnGray,
    cornerRadius: Dp = Dimens.radiusPill,
    borderWidthPx: Float = 0f,
    followBorderColor: Color = XhsColor.Red,
    followedBorderColor: Color = XhsColor.BtnGray,
) {
    val container by animateColorAsState(
        targetValue = if (followed) followedBg else followBg,
        label = "followContainer",
    )
    val borderWidth = with(LocalDensity.current) { borderWidthPx.toDp() }
    val borderColor = if (followed) followedBorderColor else followBorderColor
    Box(
        modifier = modifier
            .height(height)
            .defaultMinSize(minWidth = minWidth)
            .clip(RoundedCornerShape(cornerRadius))
            .background(container)
            .then(
                if (borderWidthPx > 0f) {
                    Modifier.border(borderWidth, borderColor, RoundedCornerShape(cornerRadius))
                } else {
                    Modifier
                },
            )
            // 触控热区扩到 ≥44（线框 T-4c：小图标 24 视觉 + 透明扩边）
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (followed) "已关注" else "关注",
            style = XhsType.buttonLabelSmall,
            color = if (followed) followedFg else followFg,
        )
    }
}

/** 他人主页（F2）通栏关注按钮：与 [XhsFollowPill] 同一状态机，只是尺寸不同。 */
@Composable
fun XhsFollowWideButton(
    followed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container by animateColorAsState(
        targetValue = if (followed) XhsColor.BtnGray else XhsColor.Red,
        label = "followWideContainer",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.buttonFollow + 4.dp)
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .background(container)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (followed) "已关注" else "关注",
            style = XhsType.buttonLabel,
            color = if (followed) XhsColor.BtnGrayText else Color.White,
        )
    }
}

/**
 * 筛选 chip（B1 频道、F1 公开/私密/合集、F3-2 性别选项）：高 28，选中黑底白字。
 */
@Composable
fun XhsFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(Dimens.chipFilter)
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .background(if (selected) XhsColor.Text1 else XhsColor.Bg)
            .border(
                width = 0.5.dp,
                color = if (selected) XhsColor.Text1 else XhsColor.Text3,
                shape = RoundedCornerShape(Dimens.radiusPill),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = XhsType.meta,
            color = if (selected) Color.White else XhsColor.Text1,
        )
    }
}

/**
 * 无边框文字 chip（B2 搜索历史 32dp / H1 建议问题 44dp）。
 * [height] 决定用途：历史 32、建议问题 44。
 */
@Composable
fun XhsTextChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = Dimens.chipHistory,
    backgroundColor: Color = XhsColor.BgGray,
    contentColor: Color = XhsColor.Text1,
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = XhsType.searchEntry, color = contentColor)
    }
}

/**
 * 数字角标（G1/底 Tab）：红底白字圆形，>99 显示 99+，高 16、最小宽 16。
 */
@Composable
fun XhsCountBadge(
    count: Long,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = Dimens.badgeMinWidth, minHeight = Dimens.badgeHeight)
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .background(XhsColor.Red)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = com.xiaohongshu.app.core.util.Formatters.formatBadge(count),
            style = XhsType.s(11, emphasis = true),
            color = XhsColor.OnBadge,
        )
    }
}

/** 纯红点（8dp，无数字时的未读提示）。 */
@Composable
fun XhsDotBadge(
    modifier: Modifier = Modifier,
    color: Color = XhsColor.Red,
) {
    Box(
        modifier = modifier
            .size(Dimens.unreadDot)
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .background(color),
    )
}

/**
 * 计数标签（C1-1/C2-1 底栏 ♥★💬）。
 *
 * 线框规则：计数为 0 时显示**文字标签**（「赞」「收藏」「评论」），>0 显示数字，
 * ≥10000 收敛为「1.2万」。
 */
@Composable
fun XhsCountText(
    count: Int,
    zeroLabel: String,
    modifier: Modifier = Modifier,
    color: Color = XhsColor.Text1,
) {
    Text(
        text = if (count > 0) com.xiaohongshu.app.core.util.Formatters.formatCount(count) else zeroLabel,
        style = XhsType.body,
        color = color,
        maxLines = 1,
        modifier = modifier,
    )
}

/**
 * 底栏互动按钮：图标 30 + 计数，整格可点（触区 ≥44）。
 * 激活态用品牌红（点赞/收藏已激活）。
 */
@Composable
fun XhsInteractionAction(
    iconRes: Int,
    activeIconRes: Int = iconRes,
    active: Boolean,
    count: Int,
    zeroLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = Dimens.iconInteract,
    tint: Color = XhsColor.Text1,
    activeTint: Color = XhsColor.Red,
) {
    Row(
        modifier = modifier
            .defaultMinSize(minWidth = Dimens.minTouchTarget, minHeight = Dimens.minTouchTarget)
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(if (active) activeIconRes else iconRes),
            contentDescription = null,
            tint = if (active) activeTint else tint,
            modifier = Modifier.size(iconSize),
        )
        XhsCountText(
            count = count,
            zeroLabel = zeroLabel,
            color = if (active) activeTint else tint,
        )
    }
}

/** 纯图标按钮，视觉尺寸 [iconSize]，触区自动扩到 ≥44。 */
@Composable
fun XhsIconButton(
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = XhsColor.Text1,
    iconSize: Dp = Dimens.icon24,
    minTouchTarget: Dp = Dimens.minTouchTarget,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = minTouchTarget, minHeight = minTouchTarget)
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** 顶栏文字动作（F3「保存」、G2「一键已读」）。 */
@Composable
fun XhsTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = XhsColor.Text1,
) {
    Text(
        text = text,
        style = XhsType.s(17),
        color = if (enabled) color else XhsColor.Text3,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/** 顶部发布按钮（E2 右上「发布」）。 */
@Composable
fun XhsPublishButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Box(
        modifier = modifier
            .height(Dimens.writeTextNextHeight)
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .background(if (enabled && !loading) XhsColor.Red else XhsColor.Red.copy(alpha = 0.45f))
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (loading) "发布中..." else "发布",
            style = XhsType.buttonLabelSmall,
            color = Color.White,
        )
    }
}

/**
 * 字数计数。
 * [remaining] = true 时显示「剩余 N / max」（E2 标题/正文口径）；否则显示「已用/上限」（F3-1 口径）。
 */
@Composable
fun XhsCharCounter(
    current: Int,
    max: Int,
    modifier: Modifier = Modifier,
    remaining: Boolean = false,
) {
    Text(
        text = if (remaining) "剩余 ${(max - current).coerceAtLeast(0)} / $max" else "$current/$max",
        style = XhsType.s(12),
        color = if (current > max) XhsColor.Error else XhsColor.Text2,
        modifier = modifier,
        textAlign = TextAlign.End,
    )
}

/** 通用分隔线（0.6dp hairline）。 */
@Composable
fun XhsDivider(
    modifier: Modifier = Modifier,
    color: Color = XhsColor.Divider,
    thickness: Dp = Dimens.hairline,
) {
    Box(modifier = modifier.fillMaxWidth().height(thickness).background(color))
}

/** 竖向分隔线（B2 搜索输入框内的「竖分隔」）。 */
@Composable
fun XhsVerticalDivider(
    modifier: Modifier = Modifier,
    height: Dp = 20.dp,
    color: Color = XhsColor.Divider,
) {
    Box(modifier = modifier.width(Dimens.hairline).height(height).background(color))
}

/** 提供统一的内容内边距常量，避免各页各写一份。 */
object XhsPaddings {
    val page = PaddingValues(horizontal = Dimens.pagePadding)
    val pageVertical = PaddingValues(vertical = Dimens.pagePadding)
    val all = PaddingValues(Dimens.pagePadding)
    val none = PaddingValues(0.dp)
}
