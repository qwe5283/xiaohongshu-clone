package com.xiaohongshu.app.core.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.design.XhsType
import com.xiaohongshu.app.core.list.PagedState

/**
 * 加载组件三档（线框 I3）：
 * - 页面级（[XhsPageLoading]）→ B4-1、C1-2、G5-2；
 * - 局部（[XhsInlineLoading]）→ B4-4 加载更多、C3-4 回复批次；
 * - 按钮级（[XhsPrimaryButton] 的 loading 参数）→ A5-2、E5-1。
 */
@Composable
fun XhsSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    color: Color = XhsColor.Text1,
    strokeWidth: Dp = 2.dp,
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = color,
        strokeWidth = strokeWidth,
    )
}

/** 页面级加载（B4-1）：居中转圈 +「加载中...」。 */
@Composable
fun XhsPageLoading(
    modifier: Modifier = Modifier,
    text: String = "加载中...",
    color: Color = XhsColor.Text1,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        XhsSpinner(size = 24.dp, color = color)
        Spacer(modifier = Modifier.height(Dimens.s12))
        Text(text = text, style = XhsType.meta, color = XhsColor.Text2)
    }
}

/** 局部加载（B4-4）：行内小转圈 + 提示。 */
@Composable
fun XhsInlineLoading(
    modifier: Modifier = Modifier,
    text: String = "加载更多...",
    color: Color = XhsColor.Text2,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        XhsSpinner(size = 14.dp, strokeWidth = 2.dp, color = color)
        Spacer(modifier = Modifier.width(Dimens.s8))
        Text(text = text, style = XhsType.captionSub, color = color)
    }
}

/**
 * 空数据（B4-3 / B5 / B3-2 / C1-4 / C3-2 / G5-1）。
 * [illustration] = true 时展示占位插画（矢量插画待替换）。
 */
@Composable
fun XhsEmptyState(
    text: String,
    modifier: Modifier = Modifier,
    illustration: Boolean = true,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.pagePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (illustration) {
            XhsIllustrationSlot()
            Spacer(modifier = Modifier.height(Dimens.s12))
        }
        Text(
            text = text,
            style = XhsType.s(13),
            color = XhsColor.Text2,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(modifier = Modifier.height(Dimens.s16))
            action()
        }
    }
}

/** 加载失败 + 重试（B4-2 / C1-3 / G5-3）。 */
@Composable
fun XhsErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    buttonText: String = "重试",
    messageColor: Color = XhsColor.Text2,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.pagePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = XhsType.s(13),
            color = messageColor,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Dimens.s12))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(Dimens.radiusPill))
                .background(XhsColor.Text1)
                .clickable(onClick = onRetry)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = buttonText,
                style = XhsType.buttonLabelSmall,
                color = Color.White,
            )
        }
    }
}

/** 表单错误条（A5-1 / A4 / E4）：表单上方，浅灰底细边框。 */
@Composable
fun XhsFormErrorBar(
    message: String,
    modifier: Modifier = Modifier,
) {
    if (message.isBlank()) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(XhsColor.BgGray)
            .padding(horizontal = Dimens.s12, vertical = Dimens.s8),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = XhsType.s(12),
            color = XhsColor.Text1,
            textAlign = TextAlign.Center,
        )
    }
}

/** 骨架条（C1-2 标题/正文骨架）。[widthFraction] 为父宽占比。 */
@Composable
fun XhsSkeletonBar(
    widthFraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(3.dp))
            .background(XhsColor.Skeleton),
    )
}

/**
 * 瀑布流列表的统一状态宿主。
 *
 * 把线框 B4 的四态收敛到一处，各列表页只需提供内容与重试回调，避免四态被漏实现：
 * 加载中 → 错误（整页替换）→ 空 → 内容（含底部「加载更多 / 没有更多了」）。
 */
@Composable
fun XhsListStateHost(
    state: PagedState<*>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    emptyText: String = "还没有笔记，快来发布第一条吧～",
    errorText: String = "加载失败，请稍后重试",
    showEmptyIllustration: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            // B4-1 首次加载
            state.loading && !state.hasContent -> XhsPageLoading()

            // B4-2 首次失败（无内容可保留时整页替换）
            state.error != null && !state.hasContent ->
                XhsErrorState(message = errorText, onRetry = onRetry)

            // B4-3 空数据
            state.isEmpty -> XhsEmptyState(
                text = emptyText,
                illustration = showEmptyIllustration,
            )

            else -> content()
        }
    }
}

/** 列表底部页脚（B4-4 加载更多 / B4-5 没有更多了）。 */
@Composable
fun XhsListFooter(
    state: PagedState<*>,
    modifier: Modifier = Modifier,
    noMoreText: String = "没有更多了",
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.loadingMore -> XhsInlineLoading()
            state.endReached && state.hasContent -> Text(
                text = noMoreText,
                style = XhsType.captionSub,
                color = XhsColor.Text3,
            )
        }
    }
}
