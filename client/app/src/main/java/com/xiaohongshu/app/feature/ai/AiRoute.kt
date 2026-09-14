package com.xiaohongshu.app.feature.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaohongshu.app.R
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.design.XhsType
import com.xiaohongshu.app.core.ui.SheetAction
import com.xiaohongshu.app.core.ui.XhsActionSheet
import com.xiaohongshu.app.core.ui.XhsIconButton
import com.xiaohongshu.app.core.ui.XhsTopBar
import com.xiaohongshu.app.di.LocalAppContainer
import com.xiaohongshu.app.di.appViewModel
import com.xiaohongshu.app.domain.model.ChatMessage
import com.xiaohongshu.app.navigation.AppNavigator

/**
 * H1/H2/H3 点点（推入式页面，**无底部 Tab 栏**）。
 *
 * 入口：底部 Tab「点点」、首页顶栏左上气泡、G1 的「点点」会话行（均由导航层推入本路由）。
 * 对话历史仅内存保存（契约 §11）；「新建对话」清空回 H1。
 */
@Composable
fun AiRoute(navigator: AppNavigator) {
    val container = LocalAppContainer.current
    val session by container.sessionManager.state.collectAsStateWithLifecycle()
    val vm: AiViewModel = appViewModel { AiViewModel(it.aiRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    // 交付要求用同步的 LocalClipboardManager；LocalClipboard 是 suspend API，此处保持同步调用
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    // H1 仅登录用户可达（线框：游客点「点点」Tab → A2 推入登录页）。
    // `MainScaffold` 的 Tabs.AI 分支没有做登录拦截（与线框不符，已列入交付说明），
    // 故在页面内做等效拦截：只按**进入本页时**的登录态判一次 —— 会话过期由全局 I1 负责推入，
    // 这里再判会推入第二个登录页。
    val loggedInAtEntry = remember { session.loggedIn }
    LaunchedEffect(Unit) {
        if (loggedInAtEntry) vm.loadSuggestions() else navigator.toLogin()
    }

    AiScreen(
        state = state,
        onBack = navigator::back,
        onInputChange = vm::onInputChange,
        onSend = vm::sendInput,
        onSuggestionClick = vm::send,
        onNewConversation = vm::newConversation,
        onCopy = { text ->
            // 「复制」真实可用：写系统剪贴板 + 全局 Toast 反馈（I2）
            clipboard.setText(AnnotatedString(text))
            container.toastController.show("已复制")
        },
        onNoteClick = navigator::toNote,
    )
}

/** 无状态内容层：状态全部来自 [AiUiState]。 */
@Composable
private fun AiScreen(
    state: AiUiState,
    onBack: () -> Unit,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onSuggestionClick: (String) -> Unit,
    onNewConversation: () -> Unit,
    onCopy: (String) -> Unit,
    onNoteClick: (Long, Boolean) -> Unit,
) {
    var showNewChatSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(XhsColor.Bg),
        ) {
            // 顶栏 56：返回 + icon&标题 17sp + 右「⋯」
            XhsTopBar(
                height = Dimens.detailBar,
                onBack = onBack,
                showDivider = true,
                center = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_assistant),
                            contentDescription = null,
                            tint = XhsColor.Text1,
                            modifier = Modifier.size(Dimens.icon20),
                        )
                        Spacer(modifier = Modifier.width(Dimens.s8))
                        Text(text = "点点", style = XhsType.pageTitle, color = XhsColor.Text1)
                    }
                },
                actions = {
                    XhsIconButton(
                        iconRes = R.drawable.ic_more,
                        onClick = { showNewChatSheet = true },
                        contentDescription = "更多",
                    )
                },
            )

            Box(modifier = Modifier.weight(1f)) {
                if (state.isEmpty) {
                    AiEmptyContent(
                        suggestions = state.suggestions,
                        onSuggestionClick = onSuggestionClick,
                    )
                } else {
                    AiConversation(
                        state = state,
                        onCopy = onCopy,
                        onNoteClick = onNoteClick,
                    )
                }
            }

            AiInputBar(
                input = state.input,
                thinking = state.thinking,
                onInputChange = onInputChange,
                onSend = onSend,
            )
        }

        // 「⋯」→「新建对话」：清空内存对话回 H1（H1 注：对话仅内存保存）
        XhsActionSheet(
            visible = showNewChatSheet,
            actions = listOf(SheetAction(label = NewChatLabel, key = NewChatLabel)),
            onAction = {
                showNewChatSheet = false
                onNewConversation()
            },
            onDismiss = { showNewChatSheet = false },
        )
    }
}

/** H1 空态：问候气泡 + 建议问题 chips（chips 为空时用兜底文案，永不空窗）。 */
@Composable
private fun AiEmptyContent(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.pagePadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.s12),
    ) {
        AiGreetingBubble()
        AiSuggestionChips(
            suggestions = suggestions,
            onSelect = onSuggestionClick,
        )
    }
}

/** H2 对话态：用户气泡 / AI 卡片 / 失败气泡 + 思考中的三点气泡。 */
@Composable
private fun AiConversation(
    state: AiUiState,
    onCopy: (String) -> Unit,
    onNoteClick: (Long, Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    val itemCount = state.messages.size + if (state.thinking) 1 else 0

    // 新消息 / 思考中出现时滚到底部，保证最新内容可见
    LaunchedEffect(itemCount) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = Dimens.pagePadding,
            vertical = Dimens.s12,
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.s8),
    ) {
        items(items = state.messages, key = { it.id }) { message ->
            when {
                // 用户消息：右侧灰气泡
                message.fromUser -> AiUserBubble(text = message.text)

                // H3-2 回复失败：对话流内的红色气泡
                message.state == ChatMessage.ChatState.Failed -> AiErrorBubble(text = message.text)

                // H2 AI 回复：左侧白卡片（正文支持「•」列表）+ 可选笔记卡 + 操作条
                else -> AiAnswerCard(
                    text = message.text,
                    notes = message.notes,
                    onCopy = { onCopy(message.text) },
                    // ★ / 分享 / 踩 / 重新生成：视觉占位，无行为
                    onPlaceholderAction = {},
                    onNoteClick = onNoteClick,
                )
            }
        }

        // H3-1 思考中：三点脉冲
        if (state.thinking) {
            item(key = ThinkingItemKey) { AiThinkingBubble() }
        }
    }
}

/** H1「⋯」弹层里的动作文案。 */
private const val NewChatLabel = "新建对话"

/** 思考中气泡在 LazyColumn 里的 key（不与消息 id 冲突）。 */
private const val ThinkingItemKey = "ai-thinking"
