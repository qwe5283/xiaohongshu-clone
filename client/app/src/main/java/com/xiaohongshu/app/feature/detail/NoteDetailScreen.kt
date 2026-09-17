package com.xiaohongshu.app.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaohongshu.app.R
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.design.XhsType
import com.xiaohongshu.app.core.list.rememberNearBottom
import com.xiaohongshu.app.core.ui.XhsAsyncImage
import com.xiaohongshu.app.core.ui.XhsAvatar
import com.xiaohongshu.app.core.ui.XhsDivider
import com.xiaohongshu.app.core.ui.XhsEmptyState
import com.xiaohongshu.app.core.ui.XhsErrorState
import com.xiaohongshu.app.core.ui.XhsFollowPill
import com.xiaohongshu.app.core.ui.XhsIconButton
import com.xiaohongshu.app.core.ui.XhsInlineLoading
import com.xiaohongshu.app.core.ui.XhsInteractionAction
import com.xiaohongshu.app.core.ui.XhsListFooter
import com.xiaohongshu.app.core.ui.XhsSkeletonBar
import com.xiaohongshu.app.core.ui.XhsSpinner
import com.xiaohongshu.app.core.util.Formatters
import com.xiaohongshu.app.domain.model.Comment
import com.xiaohongshu.app.domain.model.Note
import com.xiaohongshu.app.domain.model.NoteImage
import com.xiaohongshu.app.domain.model.ReplyGroupState

/*
 * C1-1 ~ C1-5 图文详情（浅色主题）。
 *
 * 布局：顶栏 56（作者头像 60 / 昵称 14sp / 关注胶囊 62×28 / ↗ 占位）
 *      → 单列 LazyColumn（大图 3:4 + 页码角标 + 圆点 → 标题/正文/时间 → 评论流）
 *      → 底栏 56（说点什么胶囊 h 40 + ♥★💬）。
 *
 * 评论是**页面内嵌流**（不是弹层面板）：头部「共 n 条评论」+ 行内输入行（与底栏同一个控件）
 * + 一级评论每页 20 条无限滚动；回复展开规则与 C3 完全一致（见 [CommentThread]）。
 */

/** C1-1 大图页码角标 inset 13。 */
private val PageBadgeInset = 13.dp

/** LazyColumn 里「共 n 条评论」所在项的索引（💬 点击滚到这里）。 */
internal const val COMMENT_HEADER_INDEX = 2

@Composable
internal fun NoteDetailScreen(
    state: NoteDetailUiState,
    myAvatar: String,
    listState: LazyListState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAuthorClick: (Long) -> Unit,
    onFollowClick: () -> Unit,
    onLikeClick: () -> Unit,
    onCollectClick: () -> Unit,
    onCommentCountClick: () -> Unit,
    onCommentLike: (Long) -> Unit,
    onReplyClick: (Comment, Comment) -> Unit,
    onExpandGroup: (Comment) -> Unit,
    onComposeStart: () -> Unit,
    onCancelCompose: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryComments: () -> Unit,
) {
    // 每页 20 条，距底约 6 项预加载下一页（§4.2）
    val nearBottom = rememberNearBottom(listState)
    LaunchedEffect(nearBottom, state.comments.endReached, state.comments.items.size) {
        if (nearBottom) onLoadMore()
    }

    // 输入态下收起键盘（返回手势）→ 输入栏复位为胶囊；有草稿则保留（不丢已输入内容）
    val imeVisible = rememberImeVisible()
    var imeWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(state.composing, imeVisible, state.draft) {
        if (!state.composing) {
            imeWasVisible = false
            return@LaunchedEffect
        }
        if (imeVisible) {
            imeWasVisible = true
        } else if (imeWasVisible) {
            imeWasVisible = false
            if (state.draft.isBlank()) onCancelCompose()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(XhsColor.Bg)
            // 手势条与输入法取较大者（底部输入栏随键盘上移）
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
    ) {
        // C1-2：加载中作者栏不渲染，只有 ← 可点
        NoteTopBar(
            note = state.note,
            followed = state.followed,
            isSelf = state.isSelf,
            onBack = onBack,
            onAuthorClick = onAuthorClick,
            onFollowClick = onFollowClick,
        )

        when {
            // C1-2 加载中
            state.loading -> NoteLoadingContent()

            // C1-3 失败：白底灰字 + 重试（底栏不渲染）
            state.note == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                XhsErrorState(message = NoteTexts.LoadFailed, onRetry = onRetry)
            }

            else -> {
                val note = state.note
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        item(key = "media") { NoteMedia(images = note.detailImages) }

                        item(key = "body") {
                            NoteBody(note = note, modifier = Modifier.padding(horizontal = DetailContentPadding))
                        }

                        item(key = "comments-header") {
                            Column(modifier = Modifier.padding(horizontal = DetailContentPadding)) {
                                XhsDivider()
                                CommentsHeader(count = state.commentTotal)
                            }
                        }

                        // 评论区行内输入：与底栏输入是同一个控件，点它聚焦底栏发一级评论（D3）
                        item(key = "comments-input") {
                            InlineCommentInputRow(
                                myAvatar = myAvatar,
                                placeholder = NoteTexts.inputPlaceholder(state.replyTarget?.nickname),
                                onComposeStart = onComposeStart,
                            )
                        }

                        when {
                            // 首屏评论加载中（详情与评论并行请求，详情先到达时的过渡）
                            state.comments.loading && !state.comments.hasContent ->
                                item(key = "comments-loading") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillParentMaxHeight(0.24f),
                                        contentAlignment = Alignment.Center,
                                    ) { XhsInlineLoading() }
                                }

                            state.comments.error != null && !state.comments.hasContent ->
                                item(key = "comments-error") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillParentMaxHeight(0.24f),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        XhsErrorState(message = NoteTexts.ListFailed, onRetry = onRetryComments)
                                    }
                                }

                            // C1-4 评论空态（页面流内嵌，非面板）
                            state.comments.isEmpty ->
                                item(key = "comments-empty") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillParentMaxHeight(0.3f),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        XhsEmptyState(text = NoteTexts.CommentsEmpty)
                                    }
                                }

                            else -> {
                                items(items = state.comments.items, key = { it.id }) { comment ->
                                    CommentThread(
                                        comment = comment,
                                        group = state.replyGroups[comment.id]
                                            ?: ReplyGroupState(total = comment.replyCount),
                                        authorId = note.authorId,
                                        onAvatarClick = onAuthorClick,
                                        onLikeClick = onCommentLike,
                                        onReplyClick = onReplyClick,
                                        onExpandClick = onExpandGroup,
                                    )
                                }
                                // B4-4/B4-5：底部局部指示器与「没有更多了」
                                item(key = "comments-footer") {
                                    XhsListFooter(state = state.comments.asPagedState())
                                }
                            }
                        }
                    }
                }

                NoteBottomBar(
                    note = note,
                    commentTotal = state.commentTotal,
                    placeholder = NoteTexts.inputPlaceholder(state.replyTarget?.nickname),
                    composing = state.composing,
                    draft = state.draft,
                    sending = state.sending,
                    onDraftChange = onDraftChange,
                    onComposeStart = onComposeStart,
                    onSend = onSend,
                    onLikeClick = onLikeClick,
                    onCollectClick = onCollectClick,
                    onCommentClick = onCommentCountClick,
                )
            }
        }
    }
}

/** C1-1 顶栏 h 56：← / 作者头像 60 / 昵称 14sp / 关注胶囊 / ↗ 分享占位。 */
@Composable
private fun NoteTopBar(
    note: Note?,
    followed: Boolean,
    isSelf: Boolean,
    onBack: () -> Unit,
    onAuthorClick: (Long) -> Unit,
    onFollowClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(XhsColor.Bg)) {
        Spacer(modifier = Modifier.statusBarsPadding())
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.detailBar)
                .padding(start = Dimens.s4, end = Dimens.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            XhsIconButton(
                iconRes = R.drawable.ic_chevron_left,
                onClick = onBack,
                contentDescription = "返回",
            )
            if (note == null) {
                // C1-2：数据未到达时作者栏不渲染，仅 ← 可点
                Spacer(modifier = Modifier.weight(1f))
            } else {
                Spacer(modifier = Modifier.width(Dimens.s4))
                // 线框实测头像 60 > 顶栏 56：用 requiredSize 让它按实测溢出显示
                XhsAvatar(
                    url = note.authorAvatar,
                    size = Dimens.avatarDetailAuthor,
                    modifier = Modifier
                        .requiredSize(Dimens.avatarDetailAuthor)
                        .clickable { onAuthorClick(note.authorId) },
                )
                Spacer(modifier = Modifier.width(Dimens.s16))
                Text(
                    text = note.authorLabel,
                    style = XhsType.topBarNickname,
                    color = XhsColor.Text1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // 点作者昵称/头像 → F2 他人主页
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onAuthorClick(note.authorId) },
                )
                // D2：自己的笔记不显示关注按钮；详情用描边胶囊（他人主页用通栏按钮）
                if (!isSelf) {
                    XhsFollowPill(followed = followed, onToggle = onFollowClick, followingBg = Color.Transparent, borderWidthPx = 2f)
                    Spacer(modifier = Modifier.width(Dimens.s8))
                }
                // ↗ 分享：线框未定义其行为 → 统一缺失素材占位（ic_placeholder）、点击 no-op
                XhsIconButton(
                    iconRes = R.drawable.ic_share,
                    onClick = {},
                    tint = XhsColor.Text1,
                    iconSize = Dimens.icon24,
                    contentDescription = "分享",
                )
            }
        }
    }
}

/** 大图区：全宽 3:4 + 左右滑动 + 多图页码角标 + 图下居中圆点（单图两者都不显示）。 */
@Composable
private fun NoteMedia(images: List<NoteImage>) {
    if (images.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { images.size })
    val multiple = images.size > 1

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(Dimens.RATIO_DETAIL_IMAGE)
                .background(XhsColor.PlaceholderBg),
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                XhsAsyncImage(
                    url = images[page].url,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    showGlyphOnFailure = false,
                )
            }
            if (multiple) {
                Text(
                    text = "${pagerState.currentPage + 1}/${images.size}",
                    style = XhsType.pageIndicator,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(PageBadgeInset)
                        .clip(RoundedCornerShape(Dimens.radiusPill))
                        .background(XhsColor.ScrimGray)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        if (multiple) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.s8),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                images.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = Dimens.carouselDotGap / 2)
                            .size(Dimens.carouselDot)
                            .clip(RoundedCornerShape(Dimens.radiusPill))
                            .background(
                                if (index == pagerState.currentPage) XhsColor.Text1 else XhsColor.DotInactive,
                            ),
                    )
                }
            }
        }
    }
}

/** 标题 16sp / 正文 14sp（保留换行）/ 时间行（`formatNoteTime`）。 */
@Composable
private fun NoteBody(note: Note, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(top = Dimens.s12, bottom = Dimens.s8)) {
        if (note.title.isNotBlank()) {
            Text(text = note.title, style = XhsType.detailTitle, color = XhsColor.Text1)
        }
        if (note.content.isNotBlank()) {
            Spacer(modifier = Modifier.height(Dimens.s8))
            Text(text = note.content, style = XhsType.body, color = XhsColor.Text1)
        }
        Spacer(modifier = Modifier.height(Dimens.s16))
        Text(
            text = Formatters.formatNoteTime(note.createdAt),
            style = XhsType.meta,
            color = XhsColor.Text2,
        )
        Spacer(modifier = Modifier.height(Dimens.s16))
    }
}

/** 评论区行内输入行：头像 +「说点什么...」胶囊 h 40。 */
@Composable
private fun InlineCommentInputRow(
    myAvatar: String,
    placeholder: String,
    onComposeStart: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DetailContentPadding, vertical = Dimens.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        XhsAvatar(url = myAvatar, size = Dimens.avatarComment)
        Spacer(modifier = Modifier.width(Dimens.s8))
        CommentInputPill(
            placeholder = placeholder,
            onClick = onComposeStart,
            modifier = Modifier.weight(1f),
        )
    }
}

/** C1-1 底栏 h 56：说点什么胶囊 h 40 + ♥/★/💬（计数 0 → 文字标签）。 */
@Composable
private fun NoteBottomBar(
    note: Note,
    commentTotal: Int,
    placeholder: String,
    composing: Boolean,
    draft: String,
    sending: Boolean,
    onDraftChange: (String) -> Unit,
    onComposeStart: () -> Unit,
    onSend: () -> Unit,
    onLikeClick: () -> Unit,
    onCollectClick: () -> Unit,
    onCommentClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        XhsDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.detailBar)
                .padding(horizontal = Dimens.s16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (composing) {
                // 输入态：胶囊变输入框 + 发送（空输入不可发送，D3）
                CommentDraftField(
                    draft = draft,
                    placeholder = placeholder,
                    onDraftChange = onDraftChange,
                    onSend = onSend,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(Dimens.s8))
                CommentSendButton(
                    enabled = draft.isNotBlank(),
                    sending = sending,
                    onClick = onSend,
                )
            } else {
                CommentInputPill(
                    placeholder = placeholder,
                    onClick = onComposeStart,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(Dimens.s12))
                XhsInteractionAction(
                    iconRes = R.drawable.ic_heart,
                    activeIconRes = R.drawable.ic_heart_filled,
                    active = note.liked,
                    count = note.likeCount,
                    zeroLabel = NoteTexts.Like,
                    onClick = onLikeClick,
                )
                XhsInteractionAction(
                    iconRes = R.drawable.ic_star,
                    activeIconRes = R.drawable.ic_star_filled,
                    active = note.collected,
                    count = note.collectCount,
                    zeroLabel = NoteTexts.Collect,
                    onClick = onCollectClick,
                )
                XhsInteractionAction(
                    iconRes = R.drawable.ic_comment,
                    active = false,
                    count = commentTotal,
                    zeroLabel = NoteTexts.Comment,
                    onClick = onCommentClick,
                )
            }
        }
    }
}

/** 底栏激活态输入框（composing 时替换胶囊，进入即聚焦并呼出键盘）。 */
@Composable
private fun CommentDraftField(
    draft: String,
    placeholder: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    DisposableEffect(Unit) {
        onDispose { keyboard?.hide() }
    }

    Box(
        modifier = modifier
            .height(Dimens.inputPillDetail)
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .background(XhsColor.BgGray)
            .padding(horizontal = Dimens.s16),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (draft.isEmpty()) {
            Text(
                text = placeholder,
                style = XhsType.inputPlaceholder,
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
                fontSize = XhsType.inputPlaceholder.fontSize,
            ),
            cursorBrush = SolidColor(XhsColor.Text1),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )
    }
}

/** C1-2 加载中：媒体区指示器 + 标题/正文骨架条。 */
@Composable
private fun NoteLoadingContent() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(Dimens.RATIO_DETAIL_IMAGE)
                .background(XhsColor.PlaceholderBg),
            contentAlignment = Alignment.Center,
        ) {
            XhsSpinner(size = Dimens.s24)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DetailContentPadding, vertical = Dimens.s12),
        ) {
            XhsSkeletonBar(widthFraction = 0.62f)
            Spacer(modifier = Modifier.height(Dimens.s8))
            XhsSkeletonBar(widthFraction = 1f, height = Dimens.s8)
            Spacer(modifier = Modifier.height(Dimens.s8))
            XhsSkeletonBar(widthFraction = 0.88f, height = Dimens.s8)
        }
    }
}

/** C1 文案（线框已定稿，不自创）。 */
internal object NoteTexts {
    const val LoadFailed = "笔记加载失败"
    const val ListFailed = "加载失败，请稍后重试"
    const val CommentsEmpty = "还没有评论哦"
    const val Like = "赞"
    const val Collect = "收藏"
    const val Comment = "评论"
    /** C1-5 空态占位文案池：每次随机取一条展示。 */
    val EmptyInput = listOf(
        "说点什么...",
        "留下你精彩的评论吧",
        "友善发言，暖心互动",
        "来说说你的看法吧",
        "分享你的想法吧",
    )

    /** C1-5：回复态占位变「回复 @昵称：」，否则从文案池随机取一条。 */
    fun inputPlaceholder(replyNickname: String?): String =
        if (replyNickname.isNullOrBlank()) EmptyInput.random() else "回复 @$replyNickname："
}
