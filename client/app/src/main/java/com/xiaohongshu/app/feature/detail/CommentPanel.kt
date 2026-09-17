package com.xiaohongshu.app.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaohongshu.app.R
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.design.XhsType
import com.xiaohongshu.app.core.list.rememberNearBottom
import com.xiaohongshu.app.core.ui.XhsAvatar
import com.xiaohongshu.app.core.ui.XhsDivider
import com.xiaohongshu.app.core.ui.XhsEmptyState
import com.xiaohongshu.app.core.ui.XhsErrorState
import com.xiaohongshu.app.core.ui.XhsIconButton
import com.xiaohongshu.app.core.ui.XhsListFooter
import com.xiaohongshu.app.core.ui.XhsPageLoading
import com.xiaohongshu.app.domain.model.Comment
import com.xiaohongshu.app.domain.model.ReplyGroupState

/*
 * C3-1 ~ C3-5 评论面板（底部圆角弹层，约 2/3 高）——**仅视频详情**使用。
 *
 * 图文详情的评论是页面内嵌流（C1-1/C1-4/C1-5），不走本面板：
 * 面板只在 C2-1 点「💬」时打开；点「说点什么」直接弹 C2-4 遮罩输入。
 */

/** 面板高度约 2/3（线框：圆角弹层约 2/3 高）。 */
internal const val PanelHeightFraction = 0.66f

/** C3-1 实测：输入行 52（`Dimens` 无该档位，就地声明并标注来源）。 */
private val PanelInputRowHeight = 52.dp

/** C3-1：表情 / @ 图标 24（线框未定义其行为 → 统一占位素材、点击 no-op）。 */
private val PanelIconSize = Dimens.icon24

/** C3 文案（线框已定稿）。 */
private object PanelTexts {
    const val CommentsEmpty = "还没有评论哦"
    const val ListFailed = "加载失败，请稍后重试"
    const val InputPlaceholder = "爱评论的人运气都不差"
    const val TabComments = "评论"
    const val TabLikeCollect = "赞和收藏"
}

/**
 * 评论面板。
 *
 * 展开态由 ViewModel 的 `Map<一级评论 id, ReplyGroupState>` 驱动（见 [CommentThread]）；
 * 面板每次关闭再打开都会重新组合本函数，因此滚动位置自然回到顶部（C3-5）。
 */
@Composable
internal fun CommentPanel(
    state: VideoDetailUiState,
    myAvatar: String,
    onClose: () -> Unit,
    onCommentLike: (Long) -> Unit,
    onReplyClick: (Comment, Comment) -> Unit,
    onAvatarClick: (Long) -> Unit,
    onExpandGroup: (Comment) -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val note = state.note
    // 面板每次打开都是一次新的组合 → 列表从顶部开始（C3-5：滚动位置回顶部）
    val listState = rememberLazyListState()
    val nearBottom = rememberNearBottom(listState)
    LaunchedEffect(nearBottom, state.comments.endReached, state.comments.items.size) {
        if (nearBottom) onLoadMore()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = Dimens.radiusSheet, topEnd = Dimens.radiusSheet))
            .background(XhsColor.Bg)
            // 键盘弹起时输入行上移；手势条与输入法取较大者
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
    ) {
        // Tab 行 h 44：评论 n / 赞和收藏 n
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.segmentBar)
                .padding(start = Dimens.s16, end = Dimens.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PanelTab(
                text = "${PanelTexts.TabComments} ${state.commentTotal}",
                selected = true,
                onClick = {},
            )
            Spacer(modifier = Modifier.width(Dimens.s16))
            // 契约里没有「谁赞/收藏了这条笔记」的接口 → 该 Tab 只展示计数，点击为视觉占位
            PanelTab(
                text = "${PanelTexts.TabLikeCollect} ${(note?.likeCount ?: 0) + (note?.collectCount ?: 0)}",
                selected = false,
                onClick = {},
            )
            Spacer(modifier = Modifier.weight(1f))
            // ≡ 排序：线框有该控件，但契约无排序接口 → 视觉占位、点击 no-op
            Box(
                modifier = Modifier.size(Dimens.icon24),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_menu),
                    contentDescription = "排序",
                    tint = XhsColor.Text1,
                    modifier = Modifier.size(Dimens.icon16),
                )
            }
            Spacer(modifier = Modifier.width(Dimens.s12))
            XhsIconButton(
                iconRes = R.drawable.ic_close,
                onClick = onClose,
                iconSize = Dimens.icon20,
                contentDescription = "关闭",
            )
        }
        XhsDivider()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.comments.error != null && !state.comments.hasContent ->
                    XhsErrorState(message = PanelTexts.ListFailed, onRetry = onRetry)

                state.comments.loading && !state.comments.hasContent -> XhsPageLoading()

                // C3-2 空数据：「还没有评论哦」（计数在 Tab 上按实际显示）
                state.comments.isEmpty -> XhsEmptyState(text = PanelTexts.CommentsEmpty)

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items = state.comments.items, key = { it.id }) { comment ->
                        CommentThread(
                            comment = comment,
                            group = state.replyGroups[comment.id]
                                ?: ReplyGroupState(total = comment.replyCount),
                            authorId = note?.authorId ?: 0L,
                            onAvatarClick = onAvatarClick,
                            onLikeClick = onCommentLike,
                            onReplyClick = onReplyClick,
                            onExpandClick = onExpandGroup,
                        )
                    }
                    // B4-4 局部指示器 / B4-5 没有更多了
                    item(key = "comments-footer") {
                        XhsListFooter(state = state.comments.asPagedState())
                    }
                }
            }
        }

        XhsDivider()
        PanelInputRow(
            myAvatar = myAvatar,
            draft = state.panelDraft,
            sending = state.panelSending,
            onDraftChange = onDraftChange,
            onSend = onSend,
        )
    }
}

/** Tab 标签（选中 15sp 强调 + 主色字，未选 15sp 次级字）。 */
@Composable
private fun PanelTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = if (selected) XhsType.s(15, emphasis = true) else XhsType.s(15),
        color = if (selected) XhsColor.Text1 else XhsColor.Text2,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.radiusCard))
            .clickable(enabled = !selected, onClick = onClick),
    )
}

/** C3-1 输入行 h 52：头像 36 +（表情 / @ + 输入框）+ 发送 64×40。 */
@Composable
private fun PanelInputRow(
    myAvatar: String,
    draft: String,
    sending: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PanelInputRowHeight)
            .padding(horizontal = Dimens.s16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        XhsAvatar(url = myAvatar, size = Dimens.avatarComment)
        Spacer(modifier = Modifier.width(Dimens.s8))
        Row(
            modifier = Modifier
                .weight(1f)
                .height(Dimens.inputPillVideo)
                .clip(RoundedCornerShape(Dimens.radiusPill))
                .background(XhsColor.BgGray)
                .padding(start = Dimens.s8, end = Dimens.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 表情 / @：线框未定义行为 → 统一占位素材、点击 no-op
            Icon(
                painter = painterResource(R.drawable.ic_placeholder),
                contentDescription = null,
                tint = XhsColor.Text2,
                modifier = Modifier.size(PanelIconSize),
            )
            Spacer(modifier = Modifier.width(Dimens.s4))
            Icon(
                painter = painterResource(R.drawable.ic_placeholder),
                contentDescription = null,
                tint = XhsColor.Text2,
                modifier = Modifier.size(PanelIconSize),
            )
            Spacer(modifier = Modifier.width(Dimens.s8))
            Box(modifier = Modifier.weight(1f)) {
                if (draft.isEmpty()) {
                    Text(
                        text = PanelTexts.InputPlaceholder,
                        style = XhsType.commentName,
                        color = XhsColor.Text3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = XhsColor.Text1,
                        fontSize = XhsType.commentName.fontSize,
                    ),
                    cursorBrush = SolidColor(XhsColor.Text1),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (draft.isNotBlank()) onSend() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(modifier = Modifier.width(Dimens.s8))
        CommentSendButton(
            enabled = draft.isNotBlank(),
            sending = sending,
            onClick = onSend,
        )
    }
}
