package com.xiaohongshu.app.feature.ai

// TODO(公共组件变更)：下列尺寸来自线框 H1/H2 实测，`core/design/Dimens` 里没有对应 token，
//   按「不许写魔法数」的要求先收敛成本文件顶部的具名常量；建议由架构负责人下沉到 Dimens：
//   - 笔记卡 164×236（H2）
//   - 笔记卡作者行图标 18（H2）
//   - 建议问题 chip 宽 200–220（H1；`XhsTextChip` 只能传 height，没有宽度约束）
//   另：`XhsIconButton` 没有 `enabled` 参数，按钮禁用只能靠 tint + 回调内判断表达。

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaohongshu.app.R
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.design.XhsType
import com.xiaohongshu.app.core.ui.PlaceholderIconRes
import com.xiaohongshu.app.core.ui.XhsAsyncImage
import com.xiaohongshu.app.core.ui.XhsAvatar
import com.xiaohongshu.app.core.ui.XhsDivider
import com.xiaohongshu.app.core.ui.XhsIconButton
import com.xiaohongshu.app.core.ui.XhsTextChip
import com.xiaohongshu.app.domain.model.Note

// ---- 线框实测尺寸（Dimens 暂无 token，见文件头 TODO）----
private val AiNoteCardWidth = 164.dp
private val AiNoteCardHeight = 236.dp
private val AiNoteAuthorIcon = 18.dp
private val SuggestionChipMinWidth = 200.dp
private val SuggestionChipMaxWidth = 220.dp

/** 用户气泡最大宽度占比（线框 H2：`max-width:62%`，右对齐灰气泡）。 */
private const val UserBubbleWidthFraction = 0.62f

/**
 * H1 空态的问候气泡（线框文案；**静态内容**，不进 [com.xiaohongshu.app.domain.model.ChatMessage]
 * 历史，因此「新建对话」清空后依然显示）。
 */
@Composable
internal fun AiGreetingBubble() {
    AiCard(modifier = Modifier.fillMaxWidth(), squareTopStart = true) {
        Text(
            text = "哈喽，你终于来了！是想继续之前的话题，还是开启全新的聊天都可以。",
            style = XhsType.body,
            color = XhsColor.Text1,
        )
    }
}

/** H2 用户消息：右侧灰气泡。 */
@Composable
internal fun AiUserBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(UserBubbleWidthFraction)
                .clip(
                    RoundedCornerShape(
                        topStart = Dimens.radiusCard,
                        // 右上角按线框做成直角（10px 0 10 10 的对应角）
                        topEnd = 0.dp,
                        bottomEnd = Dimens.radiusCard,
                        bottomStart = Dimens.radiusCard,
                    ),
                )
                .background(XhsColor.BgGray)
                .padding(horizontal = Dimens.s12, vertical = Dimens.s8),
        ) {
            Text(text = text, style = XhsType.body, color = XhsColor.Text1)
        }
    }
}

/**
 * H2 AI 回复卡片：左侧白卡片 + 正文（支持「•」列表）→ 可选笔记卡 → 操作条。
 */
@Composable
internal fun AiAnswerCard(
    text: String,
    notes: List<Note>,
    onCopy: () -> Unit,
    onPlaceholderAction: () -> Unit,
    onNoteClick: (Long, Boolean) -> Unit,
) {
    AiCard(modifier = Modifier.fillMaxWidth()) {
        AiAnswerText(text)

        if (notes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Dimens.s8))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.s8)) {
                items(items = notes, key = { it.id }) { note ->
                    AiNoteCard(note = note, onClick = { onNoteClick(note.id, note.isVideo) })
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimens.s8))

        // 操作条：复制 / ★ / 分享 ｜ 踩 / 重新生成
        AiActionRow(
            onCopy = onCopy,
            onPlaceholderAction = onPlaceholderAction,
        )
    }
}

/**
 * H3-2 失败气泡：错误以**对话流内的红色气泡**呈现（超时文案由
 * `ApiResult.userMessage()` 给出「请求超时，请检查网络」）。
 * 失败不中断会话——气泡下面的历史仍保留，用户可直接再提问。
 */
@Composable
internal fun AiErrorBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(Dimens.radiusCard))
                .background(XhsColor.Bg)
                .border(Dimens.hairline, XhsColor.Error, RoundedCornerShape(Dimens.radiusCard))
                .padding(horizontal = Dimens.s12, vertical = Dimens.s8),
        ) {
            Text(text = text, style = XhsType.body, color = XhsColor.Error)
        }
    }
}

/** H3-1 思考中：左侧白卡片（宽度贴合三点）+ 三点脉冲动画。 */
@Composable
internal fun AiThinkingBubble() {
    AiCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val transition = rememberInfiniteTransition(label = "aiThinking")
            PULSE_DELAYS.forEachIndexed { index, delayMillis ->
                val alpha by transition.animateFloat(
                    initialValue = PULSE_MIN_ALPHA,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = PULSE_PERIOD_MS),
                        repeatMode = RepeatMode.Reverse,
                        initialStartOffset = StartOffset(delayMillis),
                    ),
                    label = "aiThinkingDot$index",
                )
                Box(
                    modifier = Modifier
                        .size(Dimens.s8)
                        .alpha(alpha)
                        .clip(RoundedCornerShape(Dimens.radiusPill))
                        .background(XhsColor.Text2),
                )
            }
        }
    }
}

/**
 * 回复正文渲染（H2「支持「•」列表渲染」）。**无 Markdown 库**，规则就三条：
 * 1. 按 `\n` 拆行、逐行渲染（换行被保留）；
 * 2. 行首（去前导空白后）是 `•` / `-` / `*` 的行 → 视为列表项：去掉标记、前置 `•` 并左缩进 8；
 * 3. 空行 → 一个 4dp 间隔；其余行 → 普通段落。
 */
@Composable
internal fun AiAnswerText(text: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        text.split('\n').forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.isEmpty() -> Spacer(modifier = Modifier.height(Dimens.s4))

                line.length > 1 && line.first() in BULLET_MARKERS -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Dimens.s8, top = Dimens.s4, bottom = Dimens.s4),
                ) {
                    Text(text = "•", style = XhsType.body, color = XhsColor.Text1)
                    Spacer(modifier = Modifier.width(Dimens.s4))
                    Text(
                        text = line.drop(1).trimStart(),
                        style = XhsType.body,
                        color = XhsColor.Text1,
                    )
                }

                else -> Text(
                    text = line,
                    style = XhsType.body,
                    color = XhsColor.Text1,
                    modifier = Modifier.padding(vertical = Dimens.s4),
                )
            }
        }
    }
}

/**
 * H2 笔记卡（164×236）：图（占满剩余高度，线框实测 ≈159×159）→ 标题 17sp → 作者行（18 图标 + 昵称）。
 * 点击 → `navigator.toNote(id, isVideo)`（由调用方给出）。
 */
@Composable
private fun AiNoteCard(
    note: Note,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(AiNoteCardWidth)
            .height(AiNoteCardHeight)
            .clip(RoundedCornerShape(Dimens.radiusCard))
            .background(XhsColor.Bg)
            .border(Dimens.hairline, XhsColor.Divider, RoundedCornerShape(Dimens.radiusCard))
            .clickable(onClick = onClick),
    ) {
        // 图片用 weight 吃掉标题/作者行之外的剩余高度：标题两行时约 158×164，与实测 158.9×159.4 一致
        XhsAsyncImage(
            url = note.coverUrl,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            clip = RoundedCornerShape(topStart = Dimens.radiusCard, topEnd = Dimens.radiusCard),
            targetWidthDp = AiNoteCardWidth,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.s8),
        ) {
            Text(
                text = note.title.ifBlank { note.content },
                style = XhsType.pageTitle,
                color = XhsColor.Text1,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(Dimens.s4))

            Row(verticalAlignment = Alignment.CenterVertically) {
                XhsAvatar(url = note.authorAvatar, size = AiNoteAuthorIcon)
                Spacer(modifier = Modifier.width(Dimens.s4))
                Text(
                    text = note.authorLabel,
                    style = XhsType.meta,
                    color = XhsColor.Text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * H2 操作条：复制 / ★ / 分享 ｜ 踩 / 重新生成。
 *
 * - **复制真实可用**（写系统剪贴板，由调用方实现）；
 * - ★、分享、踩、重新生成按线框均为**视觉占位**（★与分享线框已注明，踩/重新生成同款处理）：
 *   只渲染按钮、无任何行为，故统一接到 [onPlaceholderAction]（空实现）。
 * 按钮 32×32（`Dimens.s32`）、间距 8（`Dimens.s8`），触控热区由 [XhsIconButton] 扩到 ≥44。
 */
@Composable
private fun AiActionRow(
    onCopy: () -> Unit,
    onPlaceholderAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.s8),
    ) {
        AiActionIcon(
            iconRes = PlaceholderIconRes,
            contentDescription = "复制",
            onClick = onCopy,
        )
        AiActionIcon(
            iconRes = R.drawable.ic_star,
            contentDescription = "收藏",
            onClick = onPlaceholderAction,
        )
        AiActionIcon(
            iconRes = R.drawable.ic_share,
            contentDescription = "分享",
            onClick = onPlaceholderAction,
        )

        Spacer(modifier = Modifier.weight(1f))

        AiActionIcon(
            iconRes = PlaceholderIconRes,
            contentDescription = "踩",
            onClick = onPlaceholderAction,
        )
        AiActionIcon(
            iconRes = PlaceholderIconRes,
            contentDescription = "重新生成",
            onClick = onPlaceholderAction,
        )
    }
}

@Composable
private fun AiActionIcon(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    XhsIconButton(
        iconRes = iconRes,
        onClick = onClick,
        tint = XhsColor.Text2,
        iconSize = Dimens.s32,
        contentDescription = contentDescription,
    )
}

/** H1 建议问题 chips：高 44、宽 200–220、间距 12、纵向排列、点击直接发送。 */
@Composable
internal fun AiSuggestionChips(
    suggestions: List<String>,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.s12)) {
        suggestions.forEach { suggestion ->
            XhsTextChip(
                text = suggestion,
                onClick = { onSelect(suggestion) },
                modifier = Modifier.widthIn(
                    min = SuggestionChipMinWidth,
                    max = SuggestionChipMaxWidth,
                ),
                height = Dimens.chipSuggestion,
            )
        }
    }
}

/**
 * H1/H2 底部输入条（高 48）：胶囊（语音/表情/＋ 三个 24dp 空占位 + 输入 + 发送）
 * ＋「内容由AI生成」声明行 24。
 *
 * 关于线框的「⊕」：它画在输入胶囊的右端，也就是本条唯一的发送入口。按交付要求
 * （「回车或发送按钮提交」）把它实现为**可用的发送钮**（素材用仓库已有的 `ic_ai_send`），
 * 而非空占位；空占位的是胶囊内的语音 / 表情 / ＋ 三个图标（[PlaceholderIconRes]）。
 *
 * [thinking] = true 时（H3-1）输入框与发送钮都禁用。
 */
@Composable
internal fun AiInputBar(
    input: String,
    thinking: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val canSend = input.isNotBlank() && !thinking

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(XhsColor.Bg)
            .imePadding()
            .navigationBarsPadding(),
    ) {
        XhsDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.aiInputBar)
                .padding(horizontal = Dimens.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(Dimens.radiusPill))
                    .background(XhsColor.BgGray)
                    .padding(horizontal = Dimens.s12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 语音 / 表情 / ＋：线框未定义行为 → ic_placeholder 视觉占位（无点击）
                AiInputPlaceholderIcon(contentDescription = "语音")
                AiInputPlaceholderIcon(contentDescription = "表情")
                AiInputPlaceholderIcon(contentDescription = "更多")

                Spacer(modifier = Modifier.width(Dimens.s8))

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
                        enabled = !thinking,
                        singleLine = true,
                        textStyle = XhsType.inputPlaceholder.copy(color = XhsColor.Text1),
                        cursorBrush = SolidColor(XhsColor.Text1),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // 发送（线框右下 ⊕ 位；思考中禁用 → 置灰 + 回调内拦截）
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

        // 「内容由AI生成」声明行 24
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.aiDisclaimerBar),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                text = "内容由AI生成",
                style = XhsType.aiDisclaimer,
                color = XhsColor.Text3,
            )
        }
    }
}

@Composable
private fun AiInputPlaceholderIcon(contentDescription: String) {
    Box(
        modifier = Modifier.size(Dimens.icon24),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            painter = painterResource(PlaceholderIconRes),
            contentDescription = contentDescription,
            tint = XhsColor.Text3,
            modifier = Modifier.size(Dimens.icon20),
        )
    }
}

/** 点点气泡的统一卡片外观：白底、hairline 边框、卡片圆角。 */
@Composable
private fun AiCard(
    modifier: Modifier = Modifier,
    /** true = 左上来角做成直角（线框 H1 问候气泡 `0 10 10 10`；H2 回复卡片四角同圆）。 */
    squareTopStart: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shape = if (squareTopStart) {
        RoundedCornerShape(
            topStart = 0.dp,
            topEnd = Dimens.radiusCard,
            bottomEnd = Dimens.radiusCard,
            bottomStart = Dimens.radiusCard,
        )
    } else {
        RoundedCornerShape(Dimens.radiusCard)
    }
    // 必须是 Column：一个 AI 回复卡片内部依次是「正文 → 笔记卡 → 操作条」，
    // 用 Box 会让三者互相叠在同一个原点上（正文被笔记卡盖住、操作条压在卡片图上）。
    Column(
        modifier = modifier
            .clip(shape)
            .background(XhsColor.Bg)
            .border(width = Dimens.hairline, color = XhsColor.Divider, shape = shape)
            .padding(horizontal = Dimens.s12, vertical = Dimens.s8),
    ) {
        content()
    }
}

/** H2「•」列表的行首标记（`-` / `*` 同义）。 */
private val BULLET_MARKERS = setOf('•', '-', '*')

/** H3-1 三点脉冲：周期 600ms、逐点错开 150ms。 */
private const val PULSE_PERIOD_MS = 600
private const val PULSE_MIN_ALPHA = 0.25f

/** 三个点的启动延迟（ms）。 */
private val PULSE_DELAYS = listOf(0, 150, 300)
