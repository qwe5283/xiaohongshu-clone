package com.xiaohongshu.app.feature.message

// TODO(公共组件变更)：气泡/输入条的部分尺寸在 `core/design/Dimens` 里没有对应 token，
//   按「不许写魔法数」的要求先收敛成本文件顶部的具名常量（与 feature/ai 的 AiComponents
//   同一处理方式）。若 G8 形态定稿，建议由架构负责人下沉到 Dimens：
//   - 顶栏中央头像 32

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.xiaohongshu.app.R
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.design.XhsType
import com.xiaohongshu.app.core.ui.XhsAvatar
import com.xiaohongshu.app.core.ui.XhsIconButton
import com.xiaohongshu.app.core.ui.XhsInlineLoading
import com.xiaohongshu.app.core.ui.XhsPrimaryButton
import com.xiaohongshu.app.core.util.Formatters
import com.xiaohongshu.app.domain.model.ImMessage

// ---- 实测尺寸（Dimens 暂无 token，见文件头 TODO）----
private val ChatTopBarAvatar = 32.dp

/** 相邻两条消息间隔超过 5 分钟才插时间分隔（G8）。 */
private const val DividerThresholdMs = 5 * 60 * 1000L

/**
 * G8 消息流（契约 §12.8）。
 *
 * 组件在本 feature 内自持——feature 之间零引用（架构规范 §2），不 import feature/ai。
 */
@Composable
internal fun ChatMessageList(
    messages: List<ImMessage>,
    loadingEarlier: Boolean,
    myAvatar: String,
    peerAvatar: String,
    onRetry: (ImMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // 新消息到达 / 发送成功 → 滚到底部，保证最新内容可见
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = Dimens.pagePadding,
            vertical = Dimens.s12,
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.s8),
    ) {
        if (loadingEarlier) {
            item(key = "earlier-loading") {
                XhsInlineLoading(
                    modifier = Modifier.fillMaxWidth(),
                    text = "加载更早消息...",
                )
            }
        }

        items(items = messages, key = { it.id }) { message ->
            // 时间分隔：与上一条间隔 >5min 时插一条居中灰字
            val index = messages.indexOf(message)
            val previous = messages.getOrNull(index - 1)
            if (previous == null || message.time - previous.time > DividerThresholdMs) {
                ChatTimeDivider(time = message.time)
            }
            if (message.isMine) {
                MyBubble(message = message, avatarUrl = myAvatar, onRetry = onRetry)
            } else {
                PeerBubble(message = message, avatarUrl = peerAvatar)
            }
        }
    }
}

/** 我发出的消息：右侧蓝气泡；失败态可点按重试。 */
@Composable
private fun MyBubble(
    message: ImMessage,
    avatarUrl: String,
    onRetry: (ImMessage) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Dimens.avatarComment + Dimens.s8),
            horizontalAlignment = Alignment.End,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Dimens.radiusCard))
                    .background(
                        if (message.state == ImMessage.SendState.Failed) {
                            XhsColor.Error.copy(alpha = 0.12f)
                        } else {
                            XhsColor.ChatMineBubble
                        },
                    )
                    .then(
                        if (message.state == ImMessage.SendState.Failed) {
                            Modifier.border(
                                Dimens.hairline,
                                XhsColor.Error,
                                RoundedCornerShape(Dimens.radiusCard),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .clickable(enabled = message.state == ImMessage.SendState.Failed) {
                        onRetry(message)
                    }
                    .padding(horizontal = Dimens.s12, vertical = Dimens.s8),
            ) {
                Text(
                    text = message.content,
                    style = XhsType.body,
                    color = if (message.state == ImMessage.SendState.Failed) {
                        XhsColor.Text1
                    } else {
                        XhsColor.OnChatMineBubble
                    },
                )
            }
            // 状态小字（发送中 / 发送失败，点击气泡重试）
            if (message.state != ImMessage.SendState.Sent) {
                Spacer(modifier = Modifier.height(Dimens.s4))
                Text(
                    text = if (message.state == ImMessage.SendState.Sending) "发送中" else "发送失败，点击重试",
                    style = XhsType.captionSub,
                    color = if (message.state == ImMessage.SendState.Failed) XhsColor.Error else XhsColor.Text3,
                )
            }
        }
        Spacer(modifier = Modifier.width(Dimens.s8))
        XhsAvatar(url = avatarUrl, size = Dimens.avatarComment)
    }
}

/** 对方发来的消息：左侧浅灰气泡，无边框。 */
@Composable
private fun PeerBubble(
    message: ImMessage,
    avatarUrl: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        XhsAvatar(url = avatarUrl, size = Dimens.avatarComment)
        Spacer(modifier = Modifier.width(Dimens.s8))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = Dimens.avatarComment + Dimens.s8),
        ) {
            ChatCard {
                Text(text = message.content, style = XhsType.body, color = XhsColor.Text1)
            }
        }
    }
}

/** 时间分隔（居中灰字，用笔记详情同款格式：今天 10:16 / 昨天 10:16 / 2026-09-14）。 */
@Composable
private fun ChatTimeDivider(time: Long) {
    if (time <= 0) return
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = Formatters.formatNoteTime(time),
            style = XhsType.captionSub,
            color = XhsColor.Text3,
        )
    }
}

/**
 * G8 底部输入条（契约 §12.8）。
 *
 * 结构仿 H1/H2 的 `AiInputBar`（胶囊 + 发送），但私聊**没有**语音/表情/＋占位图标，
 * 也没有「内容由AI生成」声明行——那三样是点点专属元素。
 */
@Composable
internal fun ChatInputBar(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier.padding(bottom = Dimens.s8),
) {
    val canSend = input.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(XhsColor.Bg)
            .imePadding()
            .navigationBarsPadding(),
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.msgInputBar)
                .padding(horizontal = Dimens.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(Dimens.radiusMsgInputBar))
                    .background(XhsColor.BgGray)
                    .padding(horizontal = Dimens.s12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (input.isEmpty()) {
                        Text(
                            text = "发消息...",
                            style = XhsType.inputPlaceholder,
                            color = XhsColor.Text3,
                            maxLines = 1,
                        )
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = onInputChange,
                        singleLine = true,
                        textStyle = XhsType.inputPlaceholder.copy(color = XhsColor.Text1),
                        cursorBrush = SolidColor(XhsColor.Text1),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // 发送钮
                XhsIconButton(
                    iconRes = R.drawable.ic_ai_send,
                    onClick = { if (canSend) onSend() },
                    tint = if (canSend) XhsColor.Text1 else XhsColor.Text3,
                    iconSize = Dimens.icon24,
                    minTouchTarget = Dimens.inputPillAi,
                    contentDescription = "发送",
                )
            }
        }
    }
}

/** 对方私聊气泡：浅灰底、无边框、四角圆角。 */
@Composable
private fun ChatCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.radiusCard))
            .background(XhsColor.BgGray)
            .padding(horizontal = Dimens.s12, vertical = Dimens.s8),
    ) {
        content()
    }
}
