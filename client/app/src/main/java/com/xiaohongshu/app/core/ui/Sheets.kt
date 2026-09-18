package com.xiaohongshu.app.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.design.XhsType

/** 遮罩层（rgba(0,0,0,.6)）：点它关闭弹层。 */
@Composable
fun XhsScrim(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(XhsColor.Scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    )
}

/**
 * 底部弹层容器：顶部圆角 12、白底，从底部滑入。
 * 上层请自行叠加 [XhsScrim]（多数场景用 [XhsSheetHost]）。
 */
@Composable
fun XhsBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    scrim: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
        ) {
            if (scrim) XhsScrim(onClick = onDismiss)
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(tween(220)) { it },
            exit = slideOutVertically(tween(180)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = Dimens.radiusSheet, topEnd = Dimens.radiusSheet))
                    .background(XhsColor.Bg)
                    .navigationBarsPadding(),
            ) {
                content()
            }
        }
    }
}

/** 弹层里的一行（发布入口 E1 / 性别选项 F3-2）：高 72，居中文字，0.6dp 分隔。 */
@Composable
fun XhsSheetRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    height: Dp = Dimens.publishSheetRow,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = label, style = XhsType.sheetRow, color = XhsColor.Text1)
        if (!subtitle.isNullOrBlank()) {
            Text(text = subtitle, style = XhsType.captionSub, color = XhsColor.Text2)
        }
    }
}

/**
 * 通用底部操作弹层（E1 发布入口 / F3-2 性别选项）。
 *
 * 结构（线框 T-4c）：若干行（默认高 72）+ 间隔 8 + 取消行 56。
 */
@Composable
fun XhsActionSheet(
    visible: Boolean,
    actions: List<SheetAction>,
    onAction: (SheetAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    cancelText: String = "取消",
) {
    XhsBottomSheet(visible = visible, onDismiss = onDismiss, modifier = modifier) {
        actions.forEachIndexed { index, action ->
            if (index > 0) XhsDivider()
            XhsSheetRow(
                label = action.label,
                subtitle = action.subtitle,
                onClick = { onAction(action) },
            )
        }
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.publishSheetGap)
                .background(XhsColor.BgGray),
        )
        XhsSheetRow(label = cancelText, onClick = onDismiss, height = Dimens.publishSheetCancel)
    }
}

/** 操作弹层的一行。 */
data class SheetAction(
    val label: String,
    val subtitle: String? = null,
    /** 调用方用于区分动作（如 "gallery" / "camera" / "text"）。 */
    val key: String = label,
)

/**
 * 底部确认弹层（F6 退出登录 / G6 一键已读 / E7 放弃发布）：
 * 遮罩 + 底部「确认 / 取消」两行。
 */
@Composable
fun XhsConfirmSheet(
    visible: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String = "确认",
    cancelText: String = "取消",
    /** 可选说明行（E7「草稿将丢失，确认放弃发布吗？」/ F6 / G6），显示在确认行上方。 */
    message: String? = null,
    /** 确认行是否用主色强调（默认 true，与线框底部确认/取消的层级一致）。 */
    emphasizeConfirm: Boolean = false,
) {
    XhsBottomSheet(visible = visible, onDismiss = onDismiss, modifier = modifier) {
        if (!message.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.s24, vertical = Dimens.s16),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = message,
                    style = XhsType.s(14),
                    color = XhsColor.Text2,
                    textAlign = TextAlign.Center,
                )
            }
        }
        XhsSheetRow(
            label = confirmText,
            onClick = {
                onDismiss()
                onConfirm()
            },
        )
        XhsDivider()
        XhsSheetRow(
            label = cancelText,
            onClick = onDismiss,
        )
    }
}

/**
 * 遮罩式弹出输入框 —— **公共组件**，C2-4 / C3-3 / F3-1 三处共用（线框明确要求同款）。
 *
 * 规格：底部白底条 + 输入框 + 右侧「发送」；[onSend] 为空输入时按钮禁用；
 * 点遮罩或收起键盘取消；弹层打开时自动聚焦并呼出键盘。
 *
 * @param initialText 回复场景预填（如 `回复 @昵称：`）
 * @param maxLength 非空时显示剩余计数，且**超限即禁用发送**
 *   （不做静默截断：F3-1 要求「名字>20 时停留遮罩并 Toast」，静默截断会让该校验永远不可达）
 * @param sendDisabledOverride 额外禁用条件（如格式校验不通过）
 */
@Composable
fun XhsOverlayInputBar(
    visible: Boolean,
    placeholder: String,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialText: String = "",
    maxLength: Int? = null,
    singleLine: Boolean = true,
    sendDisabledOverride: Boolean = false,
    keyboardEnabled: Boolean = true,
    /** true 时：键盘收起即视为取消（C2-4/C3-3「收起键盘取消」）。 */
    dismissOnKeyboardHide: Boolean = false,
) {
    var text by remember(visible, initialText) { mutableStateOf(initialText) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(visible) {
        if (visible && keyboardEnabled) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    if (dismissOnKeyboardHide) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val imeVisible = WindowInsets.ime.getBottom(density) > 0
        var wasVisible by remember { mutableStateOf(false) }
        LaunchedEffect(visible, imeVisible) {
            when {
                !visible -> wasVisible = false
                imeVisible -> wasVisible = true
                // 曾经弹出过键盘、现在收起 → 取消
                wasVisible -> {
                    wasVisible = false
                    onDismiss()
                }
            }
        }
    }

    val overLimit = maxLength != null && text.length > maxLength
    val canSend = text.isNotBlank() && !overLimit && !sendDisabledOverride

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
        ) {
            XhsScrim(onClick = onDismiss)
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(tween(220)) { it },
            exit = slideOutVertically(tween(180)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(XhsColor.Bg)
                    .imePadding()
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.s16, vertical = Dimens.s12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(Dimens.radiusPill))
                            .background(XhsColor.BgGray)
                            .padding(horizontal = Dimens.s16, vertical = if (singleLine) 10.dp else 8.dp),
                    ) {
                        if (text.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = XhsType.inputPlaceholder,
                                color = XhsColor.Text3,
                            )
                        }
                        BasicTextField(
                            value = text,
                            onValueChange = { new -> text = new },
                            singleLine = singleLine,
                            textStyle = TextStyle(
                                color = XhsColor.Text1,
                                fontSize = XhsType.inputPlaceholder.fontSize,
                            ),
                            cursorBrush = SolidColor(XhsColor.Text1),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                imeAction = if (singleLine) ImeAction.Send else ImeAction.Default,
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onSend = {
                                    if (canSend) {
                                        onSend(text)
                                        text = ""
                                    }
                                },
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                        )
                    }

                    if (maxLength != null) {
                        Spacer(modifier = Modifier.width(Dimens.s8))
                        XhsCharCounter(current = text.length, max = maxLength)
                    }

                    Spacer(modifier = Modifier.width(Dimens.s12))

                    // 发送钮：64×40（线框 T-4c）
                    Box(
                        modifier = Modifier
                            .width(Dimens.buttonSendWidth)
                            .height(Dimens.buttonSendHeight)
                            .clip(RoundedCornerShape(Dimens.radiusPill))
                            .background(if (canSend) XhsColor.Red else XhsColor.BtnGray)
                            .clickable(enabled = canSend) {
                                onSend(text)
                                text = ""
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "发送",
                            style = XhsType.buttonLabelSmall,
                            color = if (canSend) Color.White else XhsColor.Text2,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** 「—— 展开 N 条回复 / 展开更多回复」（C3-1/C3-5）：灰色短横线 + 灰字，行高 32。 */
@Composable
fun XhsExpandRepliesRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.expandRepliesRow)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.s8),
    ) {
        Text(text = "——", style = XhsType.meta, color = XhsColor.Divider)
        Text(text = label, style = XhsType.metaBold, color = XhsColor.Text2)
    }
}

/** 回复批次加载中（C3-4）：原位替换控件为 转圈 +「加载中」，不弹层不跳页。 */
@Composable
fun XhsExpandRepliesLoading(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.expandRepliesRow),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.s8),
    ) {
        XhsSpinner(size = 14.dp, strokeWidth = 2.dp, color = XhsColor.Text2)
        Text(text = "加载中", style = XhsType.s(12), color = XhsColor.Text2)
    }
}
