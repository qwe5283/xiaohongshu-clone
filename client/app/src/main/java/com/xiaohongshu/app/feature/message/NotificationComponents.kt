package com.xiaohongshu.app.feature.message

// TODO(公共组件变更)：本文件里的两个行内小控件需要公共组件支持，暂在 feature 内实现：
//  1) `XhsFollowPill` 的文案硬编码为「关注 / 已关注」，G4 需要「回关 / 已关注」→ 见 [FollowBackPill]；
//     建议给 XhsFollowPill 增加 `followedText` / `unfollowedText` 参数。
//  2) 条目行内「回复」小胶囊需要 28 高的描边 pill（现用 `XhsFilterChip` 借位），
//     且 `XhsIconButton` 没有 `enabled` 参数，禁用只能靠 tint 表达。

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaohongshu.app.R
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.design.XhsType
import com.xiaohongshu.app.core.ui.XhsAsyncImage
import com.xiaohongshu.app.core.ui.XhsAvatar
import com.xiaohongshu.app.core.ui.XhsDivider
import com.xiaohongshu.app.core.ui.XhsDotBadge
import com.xiaohongshu.app.core.ui.XhsFilterChip
import com.xiaohongshu.app.core.ui.XhsIconButton
import com.xiaohongshu.app.core.ui.XhsPrimaryButton
import com.xiaohongshu.app.core.util.Formatters
import com.xiaohongshu.app.data.dto.NotificationCategory
import com.xiaohongshu.app.data.dto.NotificationType
import com.xiaohongshu.app.domain.model.NotificationItem

/**
 * G2/G3/G4 的条目行。
 *
 * 结构（线框 T-3 实测）：未读红点（行首，已读行**预留同宽占位**以保证头像对齐）→
 * 头像 48 @16（点它 → F2）→ 标题 15sp @76 → 预览/时间行 13sp → 缩略图 48（无封面不渲染）。
 * 已读整行变淡（线框 opacity:.5）。
 */
@Composable
internal fun NotificationRow(
    item: NotificationItem,
    category: NotificationCategory,
    reply: InlineReplyState,
    followed: Boolean,
    commentLiked: Boolean,
    onRowClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onReplyToggle: () -> Unit,
    onReplyChange: (String) -> Unit,
    onReplySend: () -> Unit,
    onCommentLike: () -> Unit,
    onFollowBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRowClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            // 未读红点 8dp：占住左侧 16 页边距（线框：红点在行首、与标题行齐平；
            // 已读行**保留同宽占位**，使「头像 @16 / 标题 @76」与实测一致）
            Box(
                modifier = Modifier
                    .width(Dimens.pagePadding)
                    .padding(top = Dimens.s16),
                contentAlignment = Alignment.Center,
            ) {
                if (!item.read) XhsDotBadge()
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = Dimens.pagePadding, top = Dimens.s12, bottom = Dimens.s12)
                    // 已读整行变淡（线框 opacity:.5）；行内回复输入展开时不再变淡，避免输入区被压暗
                    .alpha(if (item.read && !(reply.isOpen && reply.targetId == item.id)) ReadRowAlpha else 1f),
            ) {
                Box(modifier = Modifier.clickable(onClick = onAvatarClick)) {
                    XhsAvatar(url = item.senderAvatar, size = Dimens.avatarConversation)
                }

                Spacer(modifier = Modifier.width(Dimens.s12))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.senderNickname,
                        style = XhsType.listTitle,
                        color = XhsColor.Text1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.typeText.ifBlank { NotificationType.fallbackText(item.type) },
                            style = XhsType.s(13),
                            color = XhsColor.Text2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(Dimens.s4))
                        // 通知时间用 formatRelative（§4.6：3分钟前 / 2小时前 / 昨天 / 09-14）
                        Text(
                            text = Formatters.formatRelative(item.createdAt),
                            style = XhsType.s(13),
                            color = XhsColor.Text2,
                            maxLines = 1,
                        )
                    }

                    // G3：评论/回复正文（线框「拍得太好了，求滤镜参数！」）
                    if (category == NotificationCategory.COMMENT && item.content.isNotBlank()) {
                        Spacer(modifier = Modifier.height(Dimens.s4))
                        Text(
                            text = item.content,
                            style = XhsType.body,
                            color = XhsColor.Text1,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (category == NotificationCategory.COMMENT) {
                        Spacer(modifier = Modifier.height(Dimens.s8))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.s12),
                        ) {
                            // 「回复」→ 行内展开输入 + 发送（再点一次收起）
                            RowActionSlot(onClick = onReplyToggle) {
                                XhsFilterChip(
                                    text = "回复",
                                    selected = false,
                                    onClick = onReplyToggle,
                                )
                            }
                            // ♡ 点赞该条评论（乐观更新 + 失败回滚在 InteractionStore 内）
                            XhsIconButton(
                                iconRes = if (commentLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart,
                                onClick = onCommentLike,
                                tint = if (commentLiked) XhsColor.Red else XhsColor.Text2,
                                iconSize = Dimens.icon20,
                                contentDescription = "点赞评论",
                            )
                        }

                        if (reply.isOpen && reply.targetId == item.id) {
                            Spacer(modifier = Modifier.height(Dimens.s8))
                            InlineReplyBar(
                                nickname = item.senderNickname,
                                text = reply.text,
                                sending = reply.sending,
                                onTextChange = onReplyChange,
                                onSend = onReplySend,
                            )
                        }
                    }
                }

                // 缩略图 = postCoverImage；无封面（如关注类通知）**不渲染空缩略图框**
                if (item.hasThumbnail) {
                    Spacer(modifier = Modifier.width(Dimens.s12))
                    XhsAsyncImage(
                        url = item.postCoverUrl,
                        modifier = Modifier.size(Dimens.avatarConversation),
                        clip = RoundedCornerShape(Dimens.radiusCard),
                        targetWidthDp = Dimens.avatarConversation,
                        targetHeightDp = Dimens.avatarConversation,
                    )
                }

                // G4「回关」：D2 关注状态机 + 自动已读
                if (category == NotificationCategory.FOLLOW) {
                    Spacer(modifier = Modifier.width(Dimens.s12))
                    FollowBackPill(
                        followed = followed,
                        onToggle = onFollowBack,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
        }

        XhsDivider()
    }
}

/**
 * G4「回关」按钮。
 *
 * 为什么不在 feature 内直接用 `XhsFollowPill`：公共组件的文案硬编码为「关注 / 已关注」，
 * 而线框 G4 明确要求未关注态是「回关」。这里按 `XhsFollowPill` 的同一结构（高度 `buttonFollow`、
 * pill 圆角、红/灰配色）实现，**仅文案不同**（已列入「需要改公共组件」，见文件头 TODO）。
 * 外层再套一层 ≥44 的点击盒，满足「触控热区一律 ≥44」。
 */
@Composable
private fun FollowBackPill(
    followed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container by animateColorAsState(
        targetValue = if (followed) XhsColor.BtnGray else XhsColor.Red,
        label = "followBackContainer",
    )
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = Dimens.minTouchTarget, minHeight = Dimens.minTouchTarget)
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(Dimens.buttonFollow)
                .clip(RoundedCornerShape(Dimens.radiusPill))
                .background(container)
                .padding(horizontal = Dimens.s12),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (followed) "已关注" else "回关",
                style = XhsType.buttonLabelSmall,
                color = if (followed) XhsColor.BtnGrayText else Color.White,
            )
        }
    }
}

/** 行内小胶囊（28 高）外面套 ≥44 的点击盒，并**消费**点击，避免误触整行跳转。 */
@Composable
private fun RowActionSlot(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = Dimens.minTouchTarget, minHeight = Dimens.minTouchTarget)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * G3 行内回复输入（展开在本行内，不弹遮罩面板）。
 * 占位文案与 C1-5 同款「回复 @昵称：」；「发送」用 [XhsPrimaryButton]（按钮级加载 I3）。
 */
@Composable
private fun InlineReplyBar(
    nickname: String,
    text: String,
    sending: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val canSend = text.isNotBlank() && !sending
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 消费点击：点输入区不应触发整行跳转
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = Dimens.inputPillDetail)
                .clip(RoundedCornerShape(Dimens.radiusPill))
                .background(XhsColor.BgGray)
                .padding(horizontal = Dimens.s12),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (text.isEmpty()) {
                Text(
                    text = "回复 @$nickname：",
                    style = XhsType.body,
                    color = XhsColor.Text3,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                singleLine = true,
                enabled = !sending,
                textStyle = XhsType.body.copy(color = XhsColor.Text1),
                cursorBrush = SolidColor(XhsColor.Text1),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.width(Dimens.s8))

        XhsPrimaryButton(
            text = "发送",
            onClick = onSend,
            enabled = canSend,
            loading = sending,
            loadingText = "发送中...",
            height = Dimens.buttonSendHeight,
            fillWidth = false,
            containerColor = XhsColor.Red,
            modifier = Modifier.width(Dimens.buttonSendWidth),
        )
    }
}

/** 已读条目整行变淡的比例（线框 G2 第三条 `opacity:.5`）。 */
private const val ReadRowAlpha = 0.5f
