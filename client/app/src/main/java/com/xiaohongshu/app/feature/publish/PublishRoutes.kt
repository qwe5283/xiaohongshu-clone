package com.xiaohongshu.app.feature.publish

/**
 * WS-Publish 入口：E1 发布入口弹层 / E2 表单（含 E5-1 提交中、E5-2 成功、E7 放弃确认；
 * 校验/提交失败一律 Toast，原 E4 错误条已废弃）/ E3 写文字。
 *
 * 媒体结构两态互斥（契约 §2 变更 #20）：图文态（1–9 图）与视频态（1 视频 + 1 封面，
 * 封面默认视频首帧）。E1 混选 → 只保留一个视频直接进视频态；E2 图文态选到视频 →
 * XhsConfirmSheet 二次确认后切换。
 *
 * 只有这三个 `*Route` / `*Sheet` 是包外可见入口（签名固定，`AppNavHost` 与 `MainScaffold` 直接调用）；
 * 其余实现散落在同目录的 [MediaImport]（相册/相机/权限/首帧截取）、[PublishComponents]（媒体条与表单控件）、
 * [PublishViewModel]、[WriteTextViewModel] 中。
 */

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaohongshu.app.R
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.design.XhsType
import com.xiaohongshu.app.core.publish.DraftMedia
import com.xiaohongshu.app.core.publish.PublishDraft
import com.xiaohongshu.app.core.ui.SheetAction
import com.xiaohongshu.app.core.ui.XhsActionSheet
import com.xiaohongshu.app.core.ui.XhsConfirmSheet
import com.xiaohongshu.app.core.ui.XhsIconButton
import com.xiaohongshu.app.core.ui.XhsPublishButton
import com.xiaohongshu.app.core.ui.XhsTopBar
import com.xiaohongshu.app.di.LocalAppContainer
import com.xiaohongshu.app.di.appViewModel
import com.xiaohongshu.app.navigation.AppNavigator
import kotlinx.coroutines.launch

// ==================================================================== E1 发布入口

/** 发布入口弹层的三个动作 key（从相册选择 / 拍摄 / 写文字）。 */
private const val ActionGallery = "gallery"
private const val ActionCamera = "capture"
private const val ActionText = "text"

/**
 * E1 弹层行（总高 = 72×3 + 间隔 8 + 取消 56 = 280 = `Dimens.publishSheetTotal`）。
 *
 * 本复刻**只实现「拍摄」**（无直播能力，§7 E1）。
 */
private val PublishEntryActions = listOf(
    SheetAction(label = "从相册选择", key = ActionGallery),
    SheetAction(label = "拍摄", key = ActionCamera),
    SheetAction(label = "写文字", key = ActionText),
)

/**
 * E1 发布入口弹层 —— 由 `MainScaffold` 的底部 Tab「＋」调用（调用点不可改）。
 *
 * - 从相册选择 → **系统照片选择器**（多选，无需权限）→ E2；
 * - 拍摄 → **CAMERA 运行时权限（I4，全项目唯一运行时权限）** → 系统相机 → E2；
 *   拒绝 → Toast「未授权相机，可从相册选择」并**留在 E1**（弹层不关，用户可改走相册）；
 * - 写文字 → E3；
 * - 取消 / 点遮罩 → 关闭弹层。
 *
 * 游客不会到达这里（`MainScaffold` 已用 `sessionManager.loggedIn` 拦截并推入登录页），
 * 此处仅做防御：未登录一律关闭。
 */
@Composable
fun PublishEntrySheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    navigator: AppNavigator,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session by container.sessionManager.state.collectAsStateWithLifecycle()
    val draft = container.publishDraft

    LaunchedEffect(visible, session.loggedIn) {
        if (visible && !session.loggedIn) onDismiss()
    }

    val acquisition = rememberMediaAcquisition(
        onAcquired = { items ->
            // 每次进入发布流程都从干净草稿开始（避免上一轮残留串味）
            draft.reset()
            val videos = items.filter { it.isVideo }
            if (videos.isEmpty()) {
                // 图文态：1–9 张图片，超出上限丢弃 + Toast
                val result = draft.acceptImages(items)
                if (result.imageOverflow) container.toastController.show(MsgImageLimit)
                if (result.accepted > 0) {
                    onDismiss()
                    navigator.toPublishForm()
                }
            } else {
                // 混选/纯视频 → 视频态：**仅保留一个视频**，首帧即封面（契约 §2 变更 #20）；
                // 同批的图片与多余视频丢弃（多视频 Toast 提示）
                if (videos.size > 1) container.toastController.show(MsgVideoLimit)
                val video = videos.first()
                scope.launch {
                    val cover = extractVideoCover(context, video.localPath)
                    if (cover == null) {
                        // 首帧截取失败：留在 E1，不产生半成品草稿
                        container.toastController.show(MsgCoverGenerateFailed)
                    } else {
                        draft.enterVideoMode(video, cover)
                        onDismiss()
                        navigator.toPublishForm()
                    }
                }
            }
        },
        // I4：拒绝时 Toast 已在 MediaAcquisition 内弹出，弹层保持打开 = 「回 E1」
        onCameraDenied = { },
        onFailure = { container.toastController.show(it) },
    )

    XhsActionSheet(
        visible = visible,
        actions = PublishEntryActions,
        onAction = { action ->
            when {
                !session.loggedIn -> onDismiss()

                action.key == ActionGallery -> acquisition.pickFromGallery()

                action.key == ActionCamera -> acquisition.takePhoto()

                action.key == ActionText -> {
                    draft.reset()
                    onDismiss()
                    navigator.toWriteText()
                }
            }
        },
        onDismiss = onDismiss,
    )
}

// ==================================================================== E2/E4/E5/E7 表单

/** E2 正文输入框最小高度（取 32×3 的阶梯值）。 */
private val BodyFieldMinHeight: Dp = Dimens.s32 * 3

/** E2「＋」的添加媒体弹层（与 E1 同款两行；**无**「写文字」入口 —— 表单里不生成配图）。 */
private val AddMediaActions = listOf(
    SheetAction(label = "从相册选择", key = ActionGallery),
    SheetAction(label = "拍摄", key = ActionCamera),
)

/**
 * E2 发布表单（含 E5-1 提交中 / E5-2 成功 / E7 放弃确认；校验失败 Toast）。
 *
 * 媒体已在草稿里（E1 相册/拍摄、E3 配图或 E1 混选进视频态），本页只做编辑与提交；
 * **无**话题 / 位置 / 可见范围。
 */
@Composable
fun PublishFormRoute(navigator: AppNavigator) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: PublishViewModel = appViewModel {
        PublishViewModel(
            draft = it.publishDraft,
            uploads = it.uploadRepository,
            posts = it.postRepository,
            toasts = it.toastController,
        )
    }
    val submitting by vm.submitting.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    // 组合期读取草稿的 SnapshotStateList → 增删媒体自动重组
    val media = vm.media.toList()

    // 校验/提交失败 → Toast（原 E4 错误条废弃），显示后即清空
    LaunchedEffect(error) {
        if (error.isNotEmpty()) {
            container.toastController.show(error)
            vm.clearError()
        }
    }

    val acquisition = rememberMediaAcquisition(
        onAcquired = vm::onMediaAcquired,
        onCameraDenied = { },
        onFailure = { container.toastController.show(it) },
        // 视频槽换视频：重截**新视频**首帧为新封面（③）
        onVideoPicked = { video ->
            scope.launch {
                val cover = extractVideoCover(context, video.localPath)
                if (cover == null) {
                    container.toastController.show(MsgCoverGenerateFailed)
                } else {
                    vm.replaceVideo(video, cover)
                }
            }
        },
        // 封面槽换封面：仅替换封面图，视频不动（③）
        onImagePicked = vm::replaceCover,
    )

    PublishFormScreen(
        title = vm.title,
        content = vm.content,
        media = media,
        video = vm.video,
        cover = vm.cover,
        submitting = submitting,
        showDiscardConfirm = vm.showDiscardConfirm,
        showAddMediaSheet = vm.showAddMediaSheet,
        showVideoSwitchConfirm = vm.showVideoSwitchConfirm,
        onTitleChange = vm::onTitleChange,
        onContentChange = vm::onContentChange,
        onRemoveMedia = vm::removeMedia,
        onAddMediaClick = vm::openAddMediaSheet,
        onAddMediaDismiss = vm::dismissAddMediaSheet,
        onPickFromGallery = {
            vm.dismissAddMediaSheet()
            acquisition.pickFromGallery()
        },
        onTakePhoto = {
            vm.dismissAddMediaSheet()
            acquisition.takePhoto()
        },
        onReplaceVideo = acquisition.pickVideo,
        onReplaceCover = acquisition.pickImage,
        onExitVideoMode = vm::exitVideoMode,
        // ② 确认切换：截首帧 → 清图片进视频态；失败 Toast 留在图文态。
        // pending 经**组合参数**传入（组合期捕获）：XhsConfirmSheet 确认行先 onDismiss 再
        // onConfirm，onDismiss（cancelSwitchToVideo）会清空 pendingVideo，点击时再读 VM 必得 null
        videoSwitchPending = vm.pendingVideo,
        onVideoSwitchConfirm = { pending ->
            scope.launch {
                val cover = extractVideoCover(context, pending.localPath)
                if (cover == null) {
                    container.toastController.show(MsgCoverGenerateFailed)
                } else {
                    vm.confirmSwitchToVideo(pending, cover)
                }
            }
        },
        onVideoSwitchDismiss = vm::cancelSwitchToVideo,
        onBack = vm::onBackPressed,
        // E5-2：成功 → Route 负责导航（一次性事件走回调，规范 §3）
        onPublish = { vm.submit(onSuccess = navigator::toMain) },
        // E7 确认放弃 → 丢弃草稿 + 回首页 Tab
        onDiscardConfirm = {
            vm.discardDraft()
            navigator.toMain()
        },
        onDiscardDismiss = vm::dismissDiscardConfirm,
    )
}

@Composable
private fun PublishFormScreen(
    title: String,
    content: String,
    media: List<DraftMedia>,
    video: DraftMedia?,
    cover: DraftMedia?,
    submitting: Boolean,
    showDiscardConfirm: Boolean,
    showAddMediaSheet: Boolean,
    showVideoSwitchConfirm: Boolean,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onRemoveMedia: (Int) -> Unit,
    onAddMediaClick: () -> Unit,
    onAddMediaDismiss: () -> Unit,
    onPickFromGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onReplaceVideo: () -> Unit,
    onReplaceCover: () -> Unit,
    onExitVideoMode: () -> Unit,
    videoSwitchPending: DraftMedia?,
    onVideoSwitchConfirm: (DraftMedia) -> Unit,
    onVideoSwitchDismiss: () -> Unit,
    onBack: () -> Unit,
    onPublish: () -> Unit,
    onDiscardConfirm: () -> Unit,
    onDiscardDismiss: () -> Unit,
) {
    // E5-1：提交中「← 不可返回」；其余情况系统返回按「先关内层弹层」处理，
    // 关闭弹层后与 ← 一致，都走 E7 二次确认
    BackHandler {
        when {
            submitting -> Unit
            showAddMediaSheet -> onAddMediaDismiss()
            showVideoSwitchConfirm -> onVideoSwitchDismiss()
            showDiscardConfirm -> onDiscardDismiss()
            else -> onBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(XhsColor.Bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶栏 44：← + 居中「发布笔记」+ 右上「发布」（发布在顶部右侧）
            XhsTopBar(
                title = "发布笔记",
                navigationIcon = {
                    XhsIconButton(
                        iconRes = R.drawable.ic_chevron_left,
                        // E5-1：提交中返回置灰且不可点
                        onClick = { if (!submitting) onBack() },
                        tint = if (submitting) XhsColor.Text3 else XhsColor.Text1,
                        contentDescription = "返回",
                    )
                },
                actions = {
                    XhsPublishButton(
                        onClick = onPublish,
                        enabled = !submitting,
                        loading = submitting,
                    )
                },
            )

            // 媒体条两态（契约 §2 变更 #20）：视频态双槽（视频/封面）+ 脚注、无 ＋；
            // 图文态缩略 + ＋。E5-1 提交中均不可增删（E5-1 的缩略图同样无 × / 无 ＋）
            if (video != null && cover != null) {
                VideoMediaStrip(
                    video = video,
                    cover = cover,
                    onReplaceVideo = onReplaceVideo,
                    onReplaceCover = onReplaceCover,
                    onExitVideoMode = onExitVideoMode,
                    editable = !submitting,
                )
            } else {
                MediaStrip(
                    media = media,
                    onRemove = onRemoveMedia,
                    onAdd = onAddMediaClick,
                    editable = !submitting,
                )
            }

            Column(
                modifier = Modifier.padding(
                    horizontal = Dimens.pagePadding,
                    vertical = Dimens.s8,
                ),
            ) {
                PublishFieldLabel("标题 *")
                Spacer(modifier = Modifier.height(Dimens.s4))
                PublishField(
                    value = title,
                    onValueChange = onTitleChange,
                    placeholder = "输入标题（必填）",
                )
                Spacer(modifier = Modifier.height(Dimens.s4))
                // 标题不截断：超 200 由 E4 校验拦截（E3 预填超长标题同样落到这里）
                PublishCounter(current = title.length, max = PublishDraft.MAX_TITLE)

                Spacer(modifier = Modifier.height(Dimens.s12))

                PublishFieldLabel("正文")
                Spacer(modifier = Modifier.height(Dimens.s4))
                PublishField(
                    value = content,
                    onValueChange = onContentChange,
                    placeholder = "分享你的想法...",
                    minHeight = BodyFieldMinHeight,
                    maxLength = PublishDraft.MAX_CONTENT,
                )
                Spacer(modifier = Modifier.height(Dimens.s4))
                PublishCounter(current = content.length, max = PublishDraft.MAX_CONTENT)
            }
        }

        XhsActionSheet(
            visible = showAddMediaSheet,
            actions = AddMediaActions,
            onAction = { action ->
                when (action.key) {
                    ActionGallery -> onPickFromGallery()
                    ActionCamera -> onTakePhoto()
                }
            },
            onDismiss = onAddMediaDismiss,
        )

        // ②：图文态选到视频 → 二次确认「清除已选图片、切换为视频笔记」。
        // onConfirm 用组合期捕获的 [videoSwitchPending]（组件内部先 onDismiss 清 VM 态再 onConfirm）
        XhsConfirmSheet(
            visible = showVideoSwitchConfirm,
            message = MsgVideoSwitchConfirm,
            confirmText = "切换",
            onConfirm = { videoSwitchPending?.let(onVideoSwitchConfirm) },
            onDismiss = onVideoSwitchDismiss,
        )

        // E7：遮罩 + 底部确认/取消（与 F6/G6 同款结构）
        XhsConfirmSheet(
            visible = showDiscardConfirm,
            message = "草稿将丢失，确认放弃发布吗？",
            confirmText = "放弃",
            confirmColor = XhsColor.Danger,
            cancelText = "取消",
            onConfirm = onDiscardConfirm,
            onDismiss = onDiscardDismiss,
        )
    }
}

// ==================================================================== E3 写文字

/** E3 画布描边（hairline 的 2 倍）。 */
private val CanvasBorderWidth: Dp = Dimens.hairline * 2

/**
 * E3 写文字页（顶栏 44 + 画布 + 底部「写长文」卡）。
 *
 * 「下一步」→ `POST /api/post/text-image`（契约 §2.11 直接返回可入库 URL）→ 配图入 E2 媒体列表、
 * 所写文字预填标题 → 推入 E2；失败 → Toast 并**停留本页**可重试（不允许无素材笔记）。
 * 左上 × 是**直接放弃返回**（与 E2 的 ← 不同，不弹确认）。
 */
@Composable
fun WriteTextRoute(navigator: AppNavigator) {
    val container = LocalAppContainer.current
    val vm: WriteTextViewModel = appViewModel {
        WriteTextViewModel(draft = it.publishDraft, posts = it.postRepository)
    }

    WriteTextScreen(
        text = vm.text,
        generating = vm.generating,
        onTextChange = vm::onTextChange,
        onAbandon = navigator::back,
        onNext = {
            vm.generate(
                onSuccess = navigator::toPublishForm,
                onFailure = { container.toastController.show(MsgGenerateFailed) },
            )
        },
    )
}

@Composable
private fun WriteTextScreen(
    text: String,
    generating: Boolean,
    onTextChange: (String) -> Unit,
    onAbandon: () -> Unit,
    onNext: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // 纯文字编辑器：进入即聚焦画布并呼出键盘
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(XhsColor.Bg)
            .imePadding()
            .navigationBarsPadding(),
    ) {
        // 顶栏 44：「下一步」64×32 右置（初始禁用，有输入才点亮）
        XhsTopBar(
            height = Dimens.topBar,
            navigationIcon = {
                XhsIconButton(
                    iconRes = R.drawable.ic_close,
                    onClick = onAbandon,
                    contentDescription = "放弃",
                )
            },
            actions = {
                WriteTextNextButton(
                    enabled = text.isNotBlank(),
                    generating = generating,
                    onClick = onNext,
                )
            },
        )

        WriteCanvas(
            text = text,
            onTextChange = onTextChange,
            focusRequester = focusRequester,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Dimens.pagePadding)
                .padding(bottom = Dimens.s16),
        )

        // 底部「写长文」卡高 98：**纯视觉入口**，点击聚焦画布
        WriteLongFormCard(
            onClick = {
                focusRequester.requestFocus()
                keyboard?.show()
            },
            modifier = Modifier
                .padding(horizontal = Dimens.pagePadding)
                .padding(bottom = Dimens.s16),
        )
    }
}

/** E3 书写画布：白底描边圆角卡（radiusSheet 12）+ 引号/占位文案 + 可输入画布。 */
@Composable
private fun WriteCanvas(
    text: String,
    onTextChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.radiusSheet))
            .background(XhsColor.Bg)
            .border(
                width = CanvasBorderWidth,
                color = XhsColor.Divider,
                shape = RoundedCornerShape(Dimens.radiusSheet),
            )
            .padding(horizontal = Dimens.s12, vertical = Dimens.s16),
    ) {
        if (text.isEmpty()) {
            Column {
                Text(text = "“", style = XhsType.s(24), color = XhsColor.Text3)
                Spacer(modifier = Modifier.height(Dimens.s8))
                Text(
                    text = WriteCanvasTitle,
                    style = XhsType.s(14, emphasis = true),
                    color = XhsColor.Text1,
                )
                Text(
                    text = WriteCanvasHint,
                    style = XhsType.captionSub,
                    color = XhsColor.Text2,
                )
            }
        }

        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            textStyle = XhsType.body.copy(color = XhsColor.Text1),
            cursorBrush = SolidColor(XhsColor.Text1),
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester),
        )
    }
}
