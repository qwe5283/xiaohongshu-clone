package com.xiaohongshu.app.feature.detail

import android.media.MediaPlayer
import android.net.Uri
import android.view.ViewGroup
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.xiaohongshu.app.R
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.design.XhsColors
import com.xiaohongshu.app.core.design.XhsType
import com.xiaohongshu.app.core.ui.XhsAvatar
import com.xiaohongshu.app.core.ui.XhsFollowPill
import com.xiaohongshu.app.core.ui.XhsIconButton
import com.xiaohongshu.app.core.ui.XhsInteractionAction
import com.xiaohongshu.app.core.ui.XhsOverlayInputBar
import com.xiaohongshu.app.core.ui.XhsSpinner
import com.xiaohongshu.app.domain.model.Comment
import com.xiaohongshu.app.domain.model.Note
import kotlinx.coroutines.delay

/*
 * C2-1 ~ C2-5 视频详情（深色沉浸）。
 *
 * 结构：状态栏区（黑）→ 媒体区（视频 + 叠加控件）→ 底栏区 44（黑）/ 评论面板 → 手势条（黑）。
 * 视频**不侵入**状态栏与底栏两条黑区：两条黑区在媒体区之外，用 inset padding 撑出来。
 *
 * 播放器：框架 `VideoView`（内部即 MediaPlayer）用 `AndroidView` 包装，零新依赖；
 * 不自动播放以外的控制（无暂停按钮，线框未定义），循环播放，`onDispose` 释放。
 */

/** C2-1 实测：作者行 48 / 标题行 46（`Dimens` 无对应档位，就地声明并标注来源）。 */
private val VideoAuthorRowHeight = 48.dp
private val VideoTitleRowHeight = 46.dp

/** C2-5：手指落点需在媒体区**底部 30%** 内才进入 seek。 */
private const val SEEK_ZONE_FRACTION = 0.3f

/** 进度条锚点：常态 7dp（约线粗 2 倍余），seek 时放大到 14dp（线框 C2-5）。 */
private val ProgressDotSize = 7.dp
private val SeekDotSize = 14.dp

/** 未播放部分为**高透明度**白（线框 rgba(255,255,255,.35)）。 */
private const val PROGRESS_TRACK_ALPHA = 0.35f

/** seek 态时间文本与进度条之间的 gap（线框 C2-5 margin-bottom 10）。 */
private val SeekTimeGap = 10.dp

/** 顶/底渐变遮罩的“subtle”强度（线框只要求感观，无实测值）。 */
private const val SCRIM_TOP_ALPHA = 0.45f
private const val SCRIM_BOTTOM_ALPHA = 0.55f

/** 播放进度轮询间隔（驱动进度条与 seek 锚点）。 */
private const val PROGRESS_POLL_MS = 200L

@Composable
internal fun VideoDetailScreen(
    state: VideoDetailUiState,
    myAvatar: String,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onRetry: () -> Unit,
    onAuthorClick: (Long) -> Unit,
    onFollowClick: () -> Unit,
    onLikeClick: () -> Unit,
    onCollectClick: () -> Unit,
    onOpenCommentInput: () -> Unit,
    onOpenComments: () -> Unit,
    onCloseComments: () -> Unit,
    onOverlayDismiss: () -> Unit,
    onOverlaySend: (String) -> Unit,
    onPanelDraftChange: (String) -> Unit,
    onPanelSend: () -> Unit,
    onCommentLike: (Long) -> Unit,
    onReplyClick: (Comment, Comment) -> Unit,
    onExpandGroup: (Comment) -> Unit,
    onLoadMore: () -> Unit,
    onRetryComments: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(XhsColor.Black)) {
        // C2-4 / C3-3：收起键盘即取消遮罩输入（点击遮罩的取消由公共组件负责）
        val imeVisible = rememberImeVisible()
        var imeWasVisible by remember { mutableStateOf(false) }
        LaunchedEffect(state.overlay.visible, imeVisible) {
            if (!state.overlay.visible) {
                imeWasVisible = false
                return@LaunchedEffect
            }
            if (imeVisible) {
                imeWasVisible = true
            } else if (imeWasVisible) {
                // 键盘被收起（返回手势等）→ 取消输入，回到 C2-1 / C3-1
                imeWasVisible = false
                onOverlayDismiss()
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // 状态栏区保持黑色，视频不侵入
            Spacer(modifier = Modifier.statusBarsPadding())

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    // C2-2 加载中（仅 ← 可点）
                    state.loading -> MediaLoading(onBack = onBack)

                    // C2-3 失败：黑底灰字，**无重试按钮**
                    state.note == null -> MediaError(onBack = onBack)

                    else -> VideoMediaArea(
                        note = state.note,
                        followed = state.followed,
                        isSelf = state.isSelf,
                        panelOpen = state.panelOpen,
                        onBack = onBack,
                        onSearch = onSearch,
                        onAuthorClick = onAuthorClick,
                        onFollowClick = onFollowClick,
                        onTapMedia = onCloseComments,
                    )
                }
            }

            if (state.panelOpen && state.note != null) {
                // C3-1：面板约 2/3 高，媒体区随之缩小（底栏由面板接管）
                CommentPanel(
                    state = state,
                    myAvatar = myAvatar,
                    onClose = onCloseComments,
                    onCommentLike = onCommentLike,
                    onReplyClick = onReplyClick,
                    onAvatarClick = onAuthorClick,
                    onExpandGroup = onExpandGroup,
                    onDraftChange = onPanelDraftChange,
                    onSend = onPanelSend,
                    onLoadMore = onLoadMore,
                    onRetry = onRetryComments,
                    modifier = Modifier.fillMaxHeight(PanelHeightFraction),
                )
            } else {
                // 底栏区：数据到达前也保持黑色（C2-2 说明）
                if (state.note != null && !state.loading && state.error == null) {
                    VideoBottomBar(
                        note = state.note,
                        commentTotal = state.commentTotal,
                        onCommentInput = onOpenCommentInput,
                        onLikeClick = onLikeClick,
                        onCollectClick = onCollectClick,
                        onCommentList = onOpenComments,
                    )
                } else {
                    Spacer(modifier = Modifier.height(Dimens.videoBottomBar))
                }
                Spacer(modifier = Modifier.fillMaxWidth().navigationBarsPadding())
            }
        }

        // C2-4 / C3-3 遮罩式输入（与 F3-1 共用公共组件），覆盖全屏
        XhsOverlayInputBar(
            visible = state.overlay.visible,
            placeholder = state.overlay.placeholder,
            initialText = state.overlay.initialText,
            onSend = onOverlaySend,
            onDismiss = onOverlayDismiss,
            sendDisabledOverride = state.overlay.sending,
        )
    }
}

/** 媒体区：视频 + 叠加顶栏 / 作者栏 / 标题 / 进度条 / seek 态。 */
@Composable
private fun VideoMediaArea(
    note: Note,
    followed: Boolean,
    isSelf: Boolean,
    panelOpen: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onAuthorClick: (Long) -> Unit,
    onFollowClick: () -> Unit,
    onTapMedia: () -> Unit,
) {
    val player = rememberVideoPlayerState()
    var seeking by remember { mutableStateOf(false) }
    var seekTargetMs by remember { mutableLongStateOf(0L) }
    val duration = player.durationMs
    val position = if (seeking) seekTargetMs else player.positionMs
    val progress = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(XhsColor.Black)
            .pointerInput(note.videoUrl, panelOpen) {
                if (panelOpen) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        // 注意：duration/position 必须在回调内**实时**读取 player 的状态。
                        // 手势块只在 key 变化时重启，若在组合期捕获 duration 快照，
                        // 首次组合（播放器尚未 prepared、duration = 0）会让 seek 永远进不去。
                        val total = player.durationMs
                        // C2-5：仅当手指落在视频区底部 30% 才进入 seek
                        if (total > 0L && offset.y >= size.height * (1f - SEEK_ZONE_FRACTION)) {
                            seekTargetMs = player.positionMs
                            seeking = true
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        val total = player.durationMs
                        if (!seeking || total <= 0L) return@detectHorizontalDragGestures
                        change.consume()
                        val delta = (dragAmount / size.width * total).toLong()
                        seekTargetMs = (seekTargetMs + delta).coerceIn(0L, total)
                    },
                    onDragEnd = {
                        if (seeking) {
                            // 松手 → 跳到锚点时间并恢复 C2-1 全部控件
                            player.seekTo(seekTargetMs)
                            seeking = false
                        }
                    },
                    onDragCancel = { seeking = false },
                )
            },
    ) {
        VideoSurface(videoUrl = note.videoUrl, player = player, modifier = Modifier.fillMaxSize())

        if (!panelOpen) {
            // 顶/底渐变遮罩（叠加控件的可读性）
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(VideoTopScrimHeight)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = SCRIM_TOP_ALPHA), Color.Transparent),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(VideoBottomScrimHeight)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = SCRIM_BOTTOM_ALPHA)),
                        ),
                    ),
            )

            // 顶栏（返回 / 搜索 / 分享占位）；seek 态隐藏
            if (!seeking) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(Dimens.topBar)
                        .padding(horizontal = Dimens.s8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    XhsIconButton(
                        iconRes = R.drawable.ic_chevron_left,
                        onClick = onBack,
                        tint = Color.White,
                        contentDescription = "返回",
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    XhsIconButton(
                        iconRes = R.drawable.ic_search,
                        onClick = onSearch,
                        tint = Color.White,
                        contentDescription = "搜索",
                    )
                    Spacer(modifier = Modifier.width(Dimens.s8))
                    // ↗ 分享：线框未定义行为 → 统一占位素材、点击 no-op
                    XhsIconButton(
                        iconRes = R.drawable.ic_placeholder,
                        onClick = {},
                        tint = Color.White,
                        contentDescription = "分享（占位）",
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            ) {
                if (seeking) {
                    // C2-5：顶栏与作者栏/标题隐藏，换成时间文本（进度条上方留 gap）
                    SeekTimeText(positionMs = position, durationMs = duration)
                } else {
                    VideoAuthorRow(
                        note = note,
                        followed = followed,
                        isSelf = isSelf,
                        onAuthorClick = onAuthorClick,
                        onFollowClick = onFollowClick,
                    )
                    VideoTitleRow(note = note)
                }
                // 进度条贴视频区底边界（底栏上缘）
                VideoProgressBar(progress = progress, seeking = seeking)
            }
        } else {
            // 线框 C3-1：点遮罩关闭面板并恢复媒体区。
            // 媒体条本身不额外压暗（与线框 C3-1 的截图一致），只让点击落到「关闭」上。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTapMedia,
                    ),
            )
        }
    }
}

/** 框架播放器包装：`VideoView` + 显式持有 `MediaPlayer`（循环播放、进度、seek）。 */
@Composable
private fun VideoSurface(
    videoUrl: String,
    player: VideoPlayerState,
    modifier: Modifier = Modifier,
) {
    if (videoUrl.isBlank()) {
        Box(modifier.background(XhsColor.Black), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_placeholder),
                contentDescription = null,
                tint = XhsColor.Text3,
                modifier = Modifier.size(Dimens.avatarDetailAuthor),
            )
        }
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            VideoView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setVideoURI(Uri.parse(videoUrl))
                setOnPreparedListener { mediaPlayer ->
                    // 自动播放 + 循环（线框允许）
                    player.onPrepared(this, mediaPlayer)
                }
                setOnErrorListener { _, _, _ ->
                    player.onError()
                    true
                }
            }
        },
        // onDispose：停止播放（stopPlayback 会释放 VideoView 内部的 MediaPlayer）
        onRelease = { view -> player.release(view) },
    )

    // 进度轮询：驱动进度条与 seek 锚点
    LaunchedEffect(player.prepared) {
        while (player.prepared) {
            player.syncPosition()
            delay(PROGRESS_POLL_MS)
        }
    }
}

/**
 * 播放器状态（不放进 ViewModel：MediaPlayer 与视图生命周期绑定，随组合进出）。
 *
 * 进度/时长/seek 全部经**显式持有的 MediaPlayer** 读写（线框要求框架播放器 + MediaPlayer，
 * 不引入 media3/ExoPlayer）。
 */
internal class VideoPlayerState {

    private var mediaPlayer: MediaPlayer? = null

    var prepared by mutableStateOf(false)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set

    fun onPrepared(view: VideoView, player: MediaPlayer) {
        mediaPlayer = player
        player.isLooping = true
        durationMs = runCatching { mediaPlayer?.duration?.toLong() ?: 0L }.getOrDefault(0L)
        positionMs = 0L
        prepared = true
        view.start()
    }

    fun onError() {
        prepared = false
    }

    fun syncPosition() {
        val player = mediaPlayer ?: return
        if (!prepared) return
        positionMs = runCatching { player.currentPosition.toLong() }.getOrDefault(positionMs)
        if (durationMs <= 0L) {
            durationMs = runCatching { player.duration.toLong().coerceAtLeast(0L) }.getOrDefault(0L)
        }
    }

    fun seekTo(ms: Long) {
        val target = if (durationMs > 0L) ms.coerceIn(0L, durationMs) else ms.coerceAtLeast(0L)
        mediaPlayer?.let { player -> runCatching { player.seekTo(target.toInt()) } }
        positionMs = target
    }

    /** onDispose：释放播放器。 */
    fun release(view: VideoView) {
        runCatching { view.stopPlayback() }
        mediaPlayer = null
        prepared = false
    }
}

@Composable
private fun rememberVideoPlayerState(): VideoPlayerState = remember { VideoPlayerState() }

/** C2-1 作者行 48：头像 48 / 昵称 20sp / 关注胶囊 49×26 / 音乐碟 22。 */
@Composable
private fun VideoAuthorRow(
    note: Note,
    followed: Boolean,
    isSelf: Boolean,
    onAuthorClick: (Long) -> Unit,
    onFollowClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(VideoAuthorRowHeight)
            .padding(horizontal = Dimens.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        XhsAvatar(
            url = note.authorAvatar,
            size = Dimens.avatarVideoAuthor,
            modifier = Modifier.clickable { onAuthorClick(note.authorId) },
        )
        Spacer(modifier = Modifier.width(Dimens.s8))
        Text(
            text = note.authorLabel,
            style = XhsType.s(20, emphasis = true),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // 点作者昵称/头像 → F2 他人主页
            modifier = Modifier
                .weight(1f)
                .clickable { onAuthorClick(note.authorId) },
        )
        if (!isSelf) {
            Spacer(modifier = Modifier.width(Dimens.s8))
            // D2：与详情作者栏同一状态机，仅尺寸不同（49×26）
            XhsFollowPill(
                followed = followed,
                onToggle = onFollowClick,
                modifier = Modifier
                    .height(FollowPillSmallHeight)
                    .widthIn(min = FollowPillSmallWidth),
            )
        }
        Spacer(modifier = Modifier.width(Dimens.s12))
        // 音乐碟 22：素材缺失 → 统一占位（点击无行为定义）
        Icon(
            painter = painterResource(R.drawable.ic_placeholder),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(MusicDiscSize),
        )
    }
}

/** C2-1 标题行 46。 */
@Composable
private fun VideoTitleRow(note: Note) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(VideoTitleRowHeight)
            .padding(horizontal = Dimens.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = note.title.ifBlank { note.content },
            style = XhsType.body,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 进度条：轨道 2dp / 触区 6dp（[Dimens.videoProgressTrack] / [Dimens.videoProgressTouch]），
 * 贴视频区底边界；已播放白色、未播放高透明白、白色圆点锚点（seek 时加粗 + 锚点放大）。
 */
@Composable
private fun VideoProgressBar(
    progress: Float,
    seeking: Boolean,
    modifier: Modifier = Modifier,
) {
    val trackHeight = if (seeking) Dimens.videoProgressTouch else Dimens.videoProgressTrack
    val dotSize = if (seeking) SeekDotSize else ProgressDotSize

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.videoProgressTouch)
            .padding(horizontal = Dimens.s4),
        contentAlignment = Alignment.CenterStart,
    ) {
        val barWidth = maxWidth
        // 未播放：高透明白
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .background(Color.White.copy(alpha = PROGRESS_TRACK_ALPHA)),
        )
        // 已播放：白
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(trackHeight)
                .background(Color.White),
        )
        // 白色圆点锚点
        Box(
            modifier = Modifier
                .offset(x = barWidth * progress - dotSize / 2)
                .size(dotSize)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** C2-5 时间文本 `mm:ss / mm:ss`（位于进度条上方，留 gap）。 */
@Composable
private fun SeekTimeText(positionMs: Long, durationMs: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = SeekTimeGap),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = formatVideoTime(positionMs), style = XhsType.captionSub, color = Color.White)
        Text(text = " / ", style = XhsType.captionSub, color = XhsColor.Text2)
        Text(text = formatVideoTime(durationMs), style = XhsType.captionSub, color = XhsColor.Text2)
    }
}

/** C2-1 底栏 44：说点什么胶囊 + 赞 / 藏 / 评（icon 30、计数 17sp）。 */
@Composable
private fun VideoBottomBar(
    note: Note,
    commentTotal: Int,
    onCommentInput: () -> Unit,
    onLikeClick: () -> Unit,
    onCollectClick: () -> Unit,
    onCommentList: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.videoBottomBar)
            .padding(start = Dimens.s8, end = Dimens.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(Dimens.inputPillVideo)
                .clip(RoundedCornerShape(Dimens.radiusPill))
                .background(XhsColors.pillSurface)
                // 点「说点什么」直接弹 C2-4 遮罩输入（不经评论面板）
                .clickable(onClick = onCommentInput)
                .padding(horizontal = Dimens.s12),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = NoteTexts.EmptyInput,
                style = XhsType.meta,
                color = XhsColors.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(Dimens.s8))
        XhsInteractionAction(
            iconRes = R.drawable.ic_heart,
            activeIconRes = R.drawable.ic_heart_filled,
            active = note.liked,
            count = note.likeCount,
            zeroLabel = NoteTexts.Like,
            onClick = onLikeClick,
            tint = Color.White,
        )
        XhsInteractionAction(
            iconRes = R.drawable.ic_star,
            activeIconRes = R.drawable.ic_star_filled,
            active = note.collected,
            count = note.collectCount,
            zeroLabel = NoteTexts.Collect,
            onClick = onCollectClick,
            tint = Color.White,
        )
        // 点「💬」→ C3-1 评论列表面板
        XhsInteractionAction(
            iconRes = R.drawable.ic_comment,
            active = false,
            count = commentTotal,
            zeroLabel = NoteTexts.Comment,
            onClick = onCommentList,
            tint = Color.White,
        )
    }
}

/** C2-2 加载中：视频占位区 + 作者栏/标题骨架（状态栏与底栏保持黑色，仅 ← 可点）。 */
@Composable
private fun MediaLoading(onBack: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(XhsColor.Black),
        contentAlignment = Alignment.Center,
    ) {
        XhsSpinner(size = Dimens.s24, color = XhsColors.text2)
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.topBar)
                    .padding(horizontal = Dimens.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                XhsIconButton(
                    iconRes = R.drawable.ic_chevron_left,
                    onClick = onBack,
                    tint = Color.White,
                    contentDescription = "返回",
                )
                Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.weight(1f))
            // 骨架：深色沉浸下的中性色用 XhsColors.divider（随主题切换的访问器）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(VideoAuthorRowHeight)
                    .padding(horizontal = Dimens.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(Dimens.avatarVideoAuthor)
                        .clip(CircleShape)
                        .background(XhsColors.divider),
                )
                Spacer(modifier = Modifier.width(Dimens.s8))
                Box(
                    modifier = Modifier
                        .width(SkeletonAuthorWidth)
                        .height(Dimens.s8)
                        .clip(RoundedCornerShape(Dimens.radiusCard))
                        .background(XhsColors.divider),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(VideoTitleRowHeight)
                    .padding(horizontal = Dimens.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(Dimens.s8)
                        .clip(RoundedCornerShape(Dimens.radiusCard))
                        .background(XhsColors.divider),
                )
            }
        }
    }
}

/** C2-3 失败：黑底灰字，**无重试按钮**（← 返回后重进）。 */
@Composable
private fun MediaError(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(XhsColor.Black)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.topBar)
                .padding(horizontal = Dimens.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            XhsIconButton(
                iconRes = R.drawable.ic_chevron_left,
                onClick = onBack,
                tint = Color.White,
                contentDescription = "返回",
            )
            Spacer(modifier = Modifier.weight(1f))
        }
        Text(
            text = VideoTexts.LoadFailed,
            style = XhsType.s(13),
            color = XhsColors.text2,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** 顶/底渐变遮罩高度（含各自叠加控件）。 */
private val VideoTopScrimHeight = Dimens.topBar + Dimens.s24
private val VideoBottomScrimHeight = VideoAuthorRowHeight + VideoTitleRowHeight + Dimens.videoProgressTouch + Dimens.s24

/** C2-1：关注胶囊 49×26 / 音乐碟 22（线框实测，Dimens 无档位）。 */
private val FollowPillSmallWidth = 49.dp
private val FollowPillSmallHeight = 26.dp
private val MusicDiscSize = 22.dp

/** C2-2 骨架条宽度（线框 C2-2 的 88px 昵称条）。 */
private val SkeletonAuthorWidth = 88.dp

/** `mm:ss`（C2-5 时间文本）。 */
internal fun formatVideoTime(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0L)) / 1000L
    return "%02d:%02d".format(seconds / 60L, seconds % 60L)
}

/** C2 文案（线框已定稿，不自创）。 */
internal object VideoTexts {
    const val LoadFailed = "视频加载失败"
}
