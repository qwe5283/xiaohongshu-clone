package com.xiaohongshu.app.feature.message

// TODO(公共组件变更)：G1 会话行的缩略图/会话行本身没有专门的 Dimens token，本文件按线框实测
//   复用已有档位（`avatarConversation`=48、`radiusCard`=8）。若需要更贴合的 token，请由架构负责人
//   在 Dimens 增加（见交付说明「需要改公共组件」）。

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaohongshu.app.R
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.Waterfall
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.design.XhsType
import com.xiaohongshu.app.core.ui.PlaceholderIconRes
import com.xiaohongshu.app.core.ui.XhsCountBadge
import com.xiaohongshu.app.core.ui.XhsDivider
import com.xiaohongshu.app.core.ui.XhsDotBadge
import com.xiaohongshu.app.core.ui.XhsIconButton
import com.xiaohongshu.app.core.ui.XhsPersonGlyph
import com.xiaohongshu.app.core.ui.XhsTopBar
import com.xiaohongshu.app.core.util.Formatters
import com.xiaohongshu.app.data.dto.NotificationCategory
import com.xiaohongshu.app.di.LocalAppContainer
import com.xiaohongshu.app.domain.model.UnreadCounts
import com.xiaohongshu.app.navigation.AppNavigator

/**
 * G1 消息页（Tab 根页面）。
 *
 * 页面本身没有任何服务端列表：三入口是固定结构、会话列表按线框只有「点点」一个入口，
 * 因此本页**不需要 ViewModel**，唯一的动态数据是三个入口的未读角标，直接读
 * [com.xiaohongshu.app.core.notify.UnreadCountCenter.counts]（15s 轮询已在 core 里跑）。
 *
 * 游客拦截：`MainScaffold` 已在点「消息」Tab 时推入登录页，本页只在登录态可达。
 */
@Composable
fun MessageRoute(navigator: AppNavigator) {
    val container = LocalAppContainer.current
    val unread by container.unreadCountCenter.counts.collectAsStateWithLifecycle()

    MessageScreen(
        counts = unread,
        onEntryClick = navigator::toNotifications,
        onConversationClick = navigator::toAi,
        // 线框 G1 顶栏右上「创建」没有定义任何行为（点按目标 ≥44 由 XhsIconButton 保证），
        // 故此处为**有意的空实现**，仅渲染 ic_placeholder 占位素材。
        onCreateClick = {},
    )
}

/** 无状态内容层：状态全部来自入参，方便 Preview 与逐条对照线框。 */
@Composable
private fun MessageScreen(
    counts: UnreadCounts,
    onEntryClick: (NotificationCategory) -> Unit,
    onConversationClick: () -> Unit,
    onCreateClick: () -> Unit,
) {
    // 三入口卡宽 =(W−32)/3：三等分、无间隙、仅 16 页边距（禁止写死像素宽）
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.toFloat()
    val entryWidth = Waterfall.entryCardWidth(screenWidthDp).dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(XhsColor.Bg),
    ) {
        XhsTopBar(
            title = "消息",
            actions = {
                XhsIconButton(
                    iconRes = PlaceholderIconRes,
                    onClick = onCreateClick,
                    contentDescription = "创建",
                )
            },
        )

        // ---- 三入口区：高 112，点击区＝整卡列（含图标与标签）----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.entryRegionHeight)
                .padding(horizontal = Dimens.pagePadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EntryColumn(
                label = "赞和收藏",
                badgeCount = counts.likeCollect,
                width = entryWidth,
                onClick = { onEntryClick(NotificationCategory.LIKE_COLLECT) },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_heart),
                    contentDescription = null,
                    tint = XhsColor.Text1,
                    modifier = Modifier.size(Dimens.icon24),
                )
            }

            EntryColumn(
                label = "评论和@",
                badgeCount = counts.comment,
                width = entryWidth,
                onClick = { onEntryClick(NotificationCategory.COMMENT) },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_comment),
                    contentDescription = null,
                    tint = XhsColor.Text1,
                    modifier = Modifier.size(Dimens.icon24),
                )
            }

            EntryColumn(
                label = "新增关注",
                badgeCount = counts.follow,
                width = entryWidth,
                onClick = { onEntryClick(NotificationCategory.FOLLOW) },
            ) {
                // 结构性字形（core 已用 Canvas 精确绘制），不用占位素材
                XhsPersonGlyph(
                    size = Dimens.icon24,
                    color = XhsColor.Text1,
                    filled = false,
                )
            }
        }

        XhsDivider()

        // ---- 会话列表：线框只有「点点」一个入口 ----
        ConversationRow(
            title = "点点",
            preview = ConversationPreview,
            timeText = Formatters.formatConversationTime(System.currentTimeMillis()),
            unread = false,
            pinned = true,
            onClick = onConversationClick,
        )
    }
}

/**
 * 三入口卡列（G1）。
 *
 * 关键点（线框注）：**点击区＝整卡列**（宽 `(W−32)/3` × 高 104，含图标与标签整块可点）；
 * 48×48 圆角 14 的浅色方块只是**视觉图标范围**，不是点击边界；未读为 0 时角标隐藏
 * （[XhsCountBadge] 在 `count <= 0` 时直接不渲染）。
 */
@Composable
private fun EntryColumn(
    label: String,
    badgeCount: Long,
    width: Dp,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(Dimens.entryCardHeight)
            .clip(RoundedCornerShape(Dimens.radiusCard))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(Dimens.entryIconBox)
                        .clip(RoundedCornerShape(Dimens.entryIconRadius))
                        // 浅色底、无边框（线框实测）
                        .background(XhsColor.BgGray),
                    contentAlignment = Alignment.Center,
                ) {
                    icon()
                }
                XhsCountBadge(
                    count = badgeCount,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = Dimens.s4, y = -Dimens.s4),
                )
            }
            // label 13sp 在图标下 12
            Spacer(modifier = Modifier.height(Dimens.s12))
            Text(
                text = label,
                style = XhsType.s(13),
                color = XhsColor.Text1,
                maxLines = 1,
            )
        }
    }
}

/**
 * 会话行（G1，高 72）。
 *
 * 线框实测：头像位 48 @16、标题 15sp @76（= 16 + 48 + 12）、预览 13sp、时间右侧；
 * 未读 8dp 红点 / 置顶 12dp 图标「按需显示」。
 *
 * 「行首 ai 小标」：线框该行最左侧是一个 24×24 圆角小标（浅色底 + `ai` 字样）。本实现把它
 * 放大到 48dp 的**头像位**（同一格既承担实测的「头像 48 @16」，又是行首标识），
 * 否则「小标在最左」与「头像 @16 / 标题 @76」两条实测无法同时成立。
 */
@Composable
private fun ConversationRow(
    title: String,
    preview: String,
    timeText: String,
    unread: Boolean,
    pinned: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.rowConversation)
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.pagePadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.avatarConversation)
                .clip(RoundedCornerShape(Dimens.radiusCard))
                .background(XhsColor.BgGray),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = AiMarkerText, style = XhsType.meta, color = XhsColor.Text2)
        }

        Spacer(modifier = Modifier.width(Dimens.s12))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = XhsType.listTitle,
                color = XhsColor.Text1,
                maxLines = 1,
            )
            Text(
                text = preview,
                style = XhsType.s(13),
                color = XhsColor.Text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(Dimens.s8))

        Column(horizontalAlignment = Alignment.End) {
            Text(text = timeText, style = XhsType.captionSub, color = XhsColor.Text2)
            // 置顶 12dp 图标（素材缺失 → ic_placeholder）
            if (pinned) {
                Icon(
                    painter = painterResource(PlaceholderIconRes),
                    contentDescription = "置顶",
                    tint = XhsColor.Text3,
                    modifier = Modifier.size(Dimens.icon12),
                )
            }
            // 未读红点 8dp
            if (unread) {
                XhsDotBadge()
            }
        }
    }
}

/** 「点点」会话的预览文案（线框 G1 行内文案）。 */
private const val ConversationPreview = "想继续之前的话题，还是开启全新的聊天都可以"

/** 会话行首的 ai 小标文案（线框 G1：灰底小方块内的 `ai`）。 */
private const val AiMarkerText = "ai"
