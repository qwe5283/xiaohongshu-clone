package com.xiaohongshu.app.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaohongshu.app.R
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.design.XhsType
import com.xiaohongshu.app.core.list.PagedState
import com.xiaohongshu.app.core.ui.XhsAvatar
import com.xiaohongshu.app.core.ui.XhsExpandRepliesLoading
import com.xiaohongshu.app.core.ui.XhsExpandRepliesRow
import com.xiaohongshu.app.core.util.Formatters
import com.xiaohongshu.app.domain.model.Comment
import com.xiaohongshu.app.domain.model.ReplyGroupState

/*
 * 评论行与回复组的**唯一实现**：C1 页面内嵌评论流（C1-1/C1-4/C1-5）与 C3 底部面板（C3-1~C3-5）
 * 共用同一套几何、同一套展开状态机，避免两处出现规则漂移。
 */

/**
 * C3-1 实测几何（线框 T-3）：头像 36 @x=15 → 正文列 @x=61；
 * 二级回复缩进 @x=90、头像 24 → 正文列 @x=124。
 *
 * 这几个值线框给了真机实测数，但 `Dimens` 里没有对应档位（不能改公共组件），
 * 故就地声明并标注来源；建议架构侧后续收进 `Dimens`。
 */
private val CommentAvatarStart = 15.dp
internal val CommentContentStart = 61.dp
internal val ReplyAvatarStart = 90.dp

/** 头像与正文列间距 10（61 − 15 − 36 = 10，回复组同理）。 */
private val CommentAvatarGap = 10.dp

/** 用户名与评论正文列间距 4。 */
private val CommentInlineRowGap = 4.dp

/** C3-1 实测：评论 meta 行高 20。 */
private val CommentMetaRowHeight = 20.dp

/** 「作者」徽标圆角 2（线框 .bauthor 的 2px）。 */
private val BadgeRadius = 2.dp

/** C1-1 实测：标题/正文/评论区左右边距 15.1。 */
internal val DetailContentPadding = 15.1.dp

/** 「共 n 条评论」所在行：正文与评论区之间的分隔线下方。 */
private val CommentsHeaderTopPadding = 12.dp

/** 二级回复缩进动态计算。 */
internal val ReplyContentStart = Dimens.avatarDetailAuthor + DetailContentPadding + CommentAvatarGap//124.dp

/**
 * 一条评论（一级或二级）。
 *
 * [topLevel] 为该展示组的一级评论：点「回复」时 parentId 必须落到一级评论上
 * （嵌套回复也归到同一组），故二级回复的回复目标也带回 [topLevel]。
 */
@Composable
internal fun CommentRow(
    comment: Comment,
    topLevel: Comment,
    authorId: Long,
    isReply: Boolean,
    onAvatarClick: (Long) -> Unit,
    onLikeClick: (Long) -> Unit,
    onReplyClick: (Comment, Comment) -> Unit,
    modifier: Modifier = Modifier,
) {
    val avatarSize = if (isReply) Dimens.avatarReply else Dimens.avatarComment
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isReply) Modifier else Modifier.heightIn(min = Dimens.rowCommentMin))
            .padding(
                start = if (isReply) ReplyContentStart else CommentAvatarStart,
                end = Dimens.s16,
                top = Dimens.s8,
                bottom = Dimens.s8,
            ),
    ) {
        XhsAvatar(
            url = comment.avatar,
            size = avatarSize,
            modifier = Modifier.clickable { onAvatarClick(comment.userId) },
        )
        Spacer(modifier = Modifier.width(CommentAvatarGap))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.nickname.ifBlank { "小红书用户" },
                    style = XhsType.comment,
                    color = XhsColor.Text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 契约 §3.5：userId == post.userId 的评论展示「作者」徽标（客户端判定）
                if (comment.userId == authorId) {
                    Spacer(modifier = Modifier.width(Dimens.s4))
                    AuthorBadge()
                }
            }
            Spacer(modifier = Modifier.height(CommentInlineRowGap))
            Text(
                text = comment.displayContent,
                style = XhsType.comment,
                color = XhsColor.Text1,
            )
            Spacer(modifier = Modifier.height(CommentInlineRowGap))
            CommentMetaRow(
                comment = comment,
                topLevel = topLevel,
                onLikeClick = onLikeClick,
                onReplyClick = onReplyClick,
            )
        }
    }
}

/** meta 行 h 20：时间 ·「回复」·（右）♥ 计数。 */
@Composable
private fun CommentMetaRow(
    comment: Comment,
    topLevel: Comment,
    onLikeClick: (Long) -> Unit,
    onReplyClick: (Comment, Comment) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CommentMetaRowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = Formatters.formatRelative(comment.createdAt),
            style = XhsType.meta,
            color = XhsColor.Text2,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(Dimens.s8))
        Text(
            text = "回复",
            style = XhsType.metaBold,
            color = XhsColor.Text2,
            modifier = Modifier
                .clickable { onReplyClick(topLevel, comment) }
                .padding(horizontal = Dimens.s4),
        )
        Spacer(modifier = Modifier.weight(1f))
        // 线框 C3-1：赞 icon 26×20（计数为 0 时不显示数字）
        Row(
            modifier = Modifier
                .height(CommentMetaRowHeight)
                .widthIn(min = Dimens.iconCommentLike)
                .clickable { onLikeClick(comment.id) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(
                    if (comment.liked) R.drawable.ic_heart_filled else R.drawable.ic_heart,
                ),
                contentDescription = "点赞",
                tint = if (comment.liked) XhsColor.Red else XhsColor.Text2,
                modifier = Modifier.size(Dimens.icon16),
            )
            if (comment.likeCount > 0) {
                Spacer(modifier = Modifier.width(Dimens.s4))
                Text(
                    text = Formatters.formatCount(comment.likeCount),
                    style = XhsType.meta,
                    color = XhsColor.Text2,
                )
            }
        }
    }
}

/** 「作者」徽标（线框 .bauthor：细边框 + 浅灰底）。 */
@Composable
private fun AuthorBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .background(XhsColor.RedBg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text = "作者", style = XhsType.s(10, emphasis = true), color = XhsColor.Red)
    }
}

/**
 * 一级评论 + 其回复组 + 组尾控件（C3-1 / C3-4 / C3-5）。
 *
 * 组尾控件三态：
 * - 折叠 → `—— 展开 N 条回复`（N = replyCount，无上限）
 * - 加载中 → 原位 `⟳ 加载中`（不弹层不跳页）
 * - 已展开且未加载完 → `—— 展开更多回复`（无数字）
 * - **全部加载完 → 控件消失**（无「收起」），组尾直接衔接下一条评论
 */
@Composable
internal fun CommentThread(
    comment: Comment,
    group: ReplyGroupState,
    authorId: Long,
    onAvatarClick: (Long) -> Unit,
    onLikeClick: (Long) -> Unit,
    onReplyClick: (Comment, Comment) -> Unit,
    onExpandClick: (Comment) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        CommentRow(
            comment = comment,
            topLevel = comment,
            authorId = authorId,
            isReply = false,
            onAvatarClick = onAvatarClick,
            onLikeClick = onLikeClick,
            onReplyClick = onReplyClick,
        )
        // 已就地平铺的回复：按时间正序（新发的回复 createdAt 最大，自然排在组尾）
        group.replies.sortedBy { it.createdAt }.forEach { reply ->
            CommentRow(
                comment = reply,
                topLevel = comment,
                authorId = authorId,
                isReply = true,
                onAvatarClick = onAvatarClick,
                onLikeClick = onLikeClick,
                onReplyClick = onReplyClick,
            )
        }
        ReplyGroupTail(group = group, onExpandClick = { onExpandClick(comment) })
    }
}

@Composable
private fun ReplyGroupTail(
    group: ReplyGroupState,
    onExpandClick: () -> Unit,
) {
    val indent = Modifier.padding(start = ReplyContentStart)
    when {
        group.loading -> XhsExpandRepliesLoading(modifier = indent)
        group.expanded && group.replies.size >= group.total -> Unit
        group.total > 0 -> XhsExpandRepliesRow(
            label = if (group.expanded) "展开更多回复" else "展开 ${group.total} 条回复",
            onClick = onExpandClick,
            modifier = indent,
        )
        else -> Unit
    }
}

/** C1-1「说点什么...」胶囊（h 40）：评论区行内输入与底栏输入是**同一个控件**。 */
@Composable
internal fun CommentInputPill(
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = Dimens.inputPillDetail,
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .background(XhsColor.BgGray)
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.s16),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = placeholder,
            style = XhsType.inputPlaceholder,
            color = XhsColor.Text3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 「发送」胶囊 64×40（与 `XhsOverlayInputBar` 内的一致）：空输入禁用。 */
@Composable
internal fun CommentSendButton(
    enabled: Boolean,
    sending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = enabled && !sending
    Box(
        modifier = modifier
            .width(Dimens.buttonSendWidth)
            .height(Dimens.buttonSendHeight)
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .background(if (active) XhsColor.Red else XhsColor.BtnGray)
            .clickable(enabled = active, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "发送",
            style = XhsType.buttonLabelSmall,
            color = if (active) Color.White else XhsColor.Text2,
        )
    }
}

/** 「共 n 条评论」计数行（C1-1；n 含回复，与底栏 💬 一致）。 */
@Composable
internal fun CommentsHeader(count: Int, modifier: Modifier = Modifier) {
    Text(
        text = "共 $count 条评论",
        style = XhsType.body,
        color = XhsColor.Text1,
        modifier = modifier.padding(top = CommentsHeaderTopPadding),
    )
}

/** 供 `XhsListFooter` 复用四态（B4-4/B4-5）。 */
internal fun CommentsUiState.asPagedState(): PagedState<Comment> = PagedState(
    items = items,
    loading = loading,
    loadingMore = loadingMore,
    error = error,
    loaded = loaded,
    endReached = endReached,
)

/**
 * 输入法是否可见。
 *
 * 用于「收起键盘即取消输入」：C1-5 的页面内嵌输入栏与 C2-4/C3-3 的遮罩输入都靠它实现
 * （公共组件 `XhsOverlayInputBar` 不感知键盘收起，故不改它，在页面侧补这一条）。
 * 读不可用时返回 false —— 只会让该行为不生效，不会误关闭输入。
 */
@Composable
internal fun rememberImeVisible(): Boolean {
    val density = LocalDensity.current
    return WindowInsets.ime.getBottom(density) > 0
}
