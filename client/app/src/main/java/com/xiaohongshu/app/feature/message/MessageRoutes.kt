package com.xiaohongshu.app.feature.message

// TODO(公共组件变更)：G1 会话行的缩略图/会话行本身没有专门的 Dimens token，本文件按实测
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import com.xiaohongshu.app.core.net.onFailure
import com.xiaohongshu.app.core.net.userMessage
import com.xiaohongshu.app.core.ui.XhsAvatar
import com.xiaohongshu.app.core.ui.XhsConfirmSheet
import com.xiaohongshu.app.core.ui.XhsCountBadge
import com.xiaohongshu.app.core.ui.XhsDivider
import com.xiaohongshu.app.core.ui.XhsDotBadge
import com.xiaohongshu.app.core.ui.XhsIconButton
import com.xiaohongshu.app.core.ui.XhsInlineLoading
import com.xiaohongshu.app.core.ui.XhsTopBar
import com.xiaohongshu.app.core.util.Formatters
import com.xiaohongshu.app.data.dto.NotificationCategory
import com.xiaohongshu.app.di.LocalAppContainer
import com.xiaohongshu.app.di.appViewModel
import com.xiaohongshu.app.domain.model.Conversation
import com.xiaohongshu.app.domain.model.UnreadCounts
import com.xiaohongshu.app.navigation.AppNavigator
import kotlinx.coroutines.launch

/**
 * G1 消息页（Tab 根页面）。
 *
 * 页面结构：顶栏（一键已读 / 创建占位）→ 三入口区（赞和收藏 / 评论和@ / 新增关注）→
 * 会话列表（「点点」固定首行 + REST 私信会话，契约 §12.2）。
 *
 * 唯一的动态数据有两份：三个入口的未读角标（读
 * [com.xiaohongshu.app.core.notify.UnreadCountCenter.counts]，进 Tab 时主动拉一次校正）与
 * 私信会话列表（[ConversationsViewModel]：进 Tab 首刷 + WS `chat.msg` 帧就地更新）。
 *
 * 游客拦截：`MainScaffold` 已在点「消息」Tab 时推入登录页，本页只在登录态可达。
 *
 * G6 一键已读的入口在本页顶栏（🧹，`ic_plus_circle` 左侧）：作用范围为**全部三分类**
 * （子页顶栏不再各自带入口；子页改为 **pop 时自动全标已读**，见 [NotificationListRoute]）。
 */
@Composable
fun MessageRoute(
    navigator: AppNavigator,
    onMarkAllReadRequest: () -> Unit,
) {
    val container = LocalAppContainer.current
    val unread by container.unreadCountCenter.counts.collectAsStateWithLifecycle()
    val vm: ConversationsViewModel = appViewModel {
        ConversationsViewModel(
            repository = it.chatRepository,
            realtime = it.realtimeConnection,
            session = it.sessionManager,
            toasts = it.toastController,
        )
    }
    val conversations by vm.state.collectAsStateWithLifecycle()

    // 进 Tab 首刷（Tab 切换会重建本组合；从 G8 返回时 MainScaffold 选中态回消息 Tab，
    // 同样会走到这里——满足「返回私聊页时刷新」的预期）。未读角标一并拉一次：
    // WS 推送是增量通道，兜住「连接健康但服务端没推帧」的漂移（如 mockserver _reset）
    LaunchedEffect(Unit) {
        vm.refresh()
        container.unreadCountCenter.refresh()
    }

    MessageScreen(
        counts = unread,
        conversations = conversations.items,
        conversationsLoading = conversations.loading,
        onEntryClick = navigator::toNotifications,
        onConversationClick = navigator::toAi,
        onChatClick = { conversation ->
            navigator.toChat(
                conversationId = conversation.id,
                peerId = conversation.peerId,
                peerNickname = conversation.peerNickname,
                peerAvatar = conversation.peerAvatar,
            )
        },
        onPeerClick = { conversation -> navigator.toUserProfile(conversation.peerId) },
        // G1「创建」尚未定义业务行为，因此保留空回调。
        onCreateClick = {},
        // G6 一键已读（入口从 G2/G3/G4 子页移入 G1）：弹层由宿主 MainScaffold 挂载
        // （页面内挂载遮罩盖不住底 Tab，见 MarkAllReadSheet）
        onMarkAllReadClick = onMarkAllReadRequest,
    )
}

/**
 * G6 一键已读确认弹层（全部三分类）。
 *
 * **挂载约定**：必须由 MainScaffold 挂在底 Tab 栏之后（同 `PublishEntrySheet` / `ProfileLogoutSheet`），
 * 挂在 G1 页面内遮罩盖不住底部导航。
 */
@Composable
fun MarkAllReadSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()

    XhsConfirmSheet(
        visible = visible,
        onConfirm = {
            // 本地立即全清 + 服务端全量已读；失败由下次未读数推送/进页拉取校正（G5-2 同款异常恢复）
            container.unreadCountCenter.clearAll()
            scope.launch {
                container.notificationRepository.markAllRead(null)
                    .onFailure { container.toastController.show(it.userMessage()) }
            }
        },
        onDismiss = onDismiss,
        message = "确认将全部消息标记为已读吗？",
        confirmText = "一键已读",
        cancelText = "取消",
    )
}

/** 无状态内容层：状态全部来自入参，方便 Preview。 */
@Composable
private fun MessageScreen(
    counts: UnreadCounts,
    conversations: List<Conversation>,
    conversationsLoading: Boolean,
    onEntryClick: (NotificationCategory) -> Unit,
    onConversationClick: () -> Unit,
    onChatClick: (Conversation) -> Unit,
    onPeerClick: (Conversation) -> Unit,
    onCreateClick: () -> Unit,
    onMarkAllReadClick: () -> Unit,
) {
    // 三入口卡宽 =(W−32)/3：三等分、无间隙、仅 16 页边距（禁止写死像素宽）
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.toFloat()
    val entryWidth = Waterfall.entryCardWidth(screenWidthDp).dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(XhsColor.Bg),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
        XhsTopBar(
            title = "消息",
            actions = {
                // 🧹 一键已读（G6，全部三分类；确认弹层由宿主挂载）
                XhsIconButton(
                    iconRes = R.drawable.ic_broom,
                    onClick = onMarkAllReadClick,
                    contentDescription = "一键已读",
                )
                XhsIconButton(
                    iconRes = R.drawable.ic_plus_circle,
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
                Icon(
                    painter = painterResource(R.drawable.ic_user),
                    contentDescription = null,
                    tint = XhsColor.Text1,
                    modifier = Modifier.size(Dimens.icon24),
                )
            }
        }

        // ---- 会话列表：「点点」固定首行 + 私信会话（契约 §12）----
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item(key = "ai-entry") {
                ConversationRow(
                    title = "点点",
                    preview = ConversationPreview,
                    timeText = Formatters.formatConversationTime(System.currentTimeMillis()),
                    unread = false,
                    pinned = true,
                    avatarUrl = null,
                    onClick = onConversationClick,
                )
            }

            items(
                items = conversations,
                key = { "chat-${it.id}" },
            ) { conversation ->
                ConversationRow(
                    title = conversation.peerDisplayName,
                    preview = conversation.lastMessage,
                    timeText = Formatters.formatConversationTime(conversation.lastTime),
                    unread = conversation.unread > 0,
                    pinned = false,
                    avatarUrl = conversation.peerAvatar,
                    onClick = { onChatClick(conversation) },
                )
            }

            if (conversationsLoading && conversations.isEmpty()) {
                item(key = "loading") {
                    XhsInlineLoading(modifier = Modifier.padding(vertical = Dimens.s16))
                }
            }
        }
        }
    }
}

/**
 * 三入口卡列（G1）。
 *
 * 关键点：**点击区＝整卡列**（宽 `(W−32)/3` × 高 104，含图标与标签整块可点）；
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
                        // 浅色底、无边框
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
                style = XhsType.meta,
                color = XhsColor.Text1,
                maxLines = 1,
            )
        }
    }
}

/**
 * 会话行（G1，高 72）。
 *
 * 实测：头像位 48 @16、标题 15sp @76（= 16 + 48 + 12）、预览 13sp、时间右侧；
 * 未读 8dp 红点 / 置顶 12dp 图标「按需显示」。
 *
 * [avatarUrl] 为空 = 「点点」行（渲染行首 `ai` 小标，原 G1 定稿样式）；
 * 非空 = 私信会话行（契约 §12），渲染 `XhsAvatar` 且头像可点 → F2。
 *
 * 「行首 ai 小标」：设计上该行最左侧是一个 24×24 圆角小标（浅色底 + `ai` 字样）。本实现把它
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
    avatarUrl: String?,
    onClick: () -> Unit,
    onAvatarClick: (() -> Unit)? = null,
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
                .then(
                    if (onAvatarClick != null) {
                        Modifier.clickable(onClick = onAvatarClick)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarUrl.isNullOrBlank()) {
                Text(text = AiMarkerText, style = XhsType.meta, color = XhsColor.Text2)
            } else {
                XhsAvatar(url = avatarUrl, size = Dimens.avatarConversation, borderWidthPx = 0f)
            }
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
                style = XhsType.meta,
                color = XhsColor.Text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(Dimens.s8))

        Column(horizontalAlignment = Alignment.End) {
            Text(text = timeText, style = XhsType.captionSub, color = XhsColor.Text2)
            // 置顶 12dp 图标
            if (pinned) {
                Icon(
                    painter = painterResource(R.drawable.ic_pin_top),
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

/** 「点点」会话的预览文案（G1 定稿文案）。 */
private const val ConversationPreview = "想继续之前的话题，还是开启全新的聊天都可以"

/** 会话行首的 ai 小标文案（G1：灰底小方块内的 `ai`）。 */
private const val AiMarkerText = "ai"
