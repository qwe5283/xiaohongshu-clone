package com.xiaohongshu.app.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.xiaohongshu.app.core.ui.XhsIconButton
import com.xiaohongshu.app.core.ui.XhsTextChip
import com.xiaohongshu.app.core.ui.XhsVerticalDivider

/** B2 输入框占位文案（线框 B2 原文）。 */
private const val SearchPlaceholder = "搜索你感兴趣的内容"

/**
 * B2 / B3-1 共用的搜索行：高 52。
 *
 * 结构（线框 B2 实测）：返回 22 → 输入框 44（右侧竖分隔 + 相机入口）→「搜索」按钮 56×44。
 * 输入框回车（IME Search）与「搜索」按钮等价：二者都走 [onSubmit]。
 */
@Composable
internal fun SearchInputRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SearchRowHeight)
            .padding(horizontal = Dimens.pagePadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.s8),
    ) {
        XhsIconButton(
            iconRes = R.drawable.ic_chevron_left,
            onClick = onBack,
            iconSize = SearchBackIconSize,
            contentDescription = "返回",
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .height(Dimens.inputSearch)
                .clip(RoundedCornerShape(Dimens.radiusPill))
                .background(XhsColor.BgGray)
                .padding(start = Dimens.s12, end = Dimens.s8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.s8),
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        text = SearchPlaceholder,
                        style = XhsType.searchEntry,
                        color = XhsColor.Text3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = XhsType.searchEntry.copy(color = XhsColor.Text1),
                    cursorBrush = SolidColor(XhsColor.Text1),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                )
            }

            XhsVerticalDivider(height = Dimens.s16)

            // 相机入口（原版「拍照搜索」）：本复刻未纳入接口契约，故仅作视觉入口；
            // 图标素材缺失，按 §4.5 使用全项目统一占位
            Icon(
                painter = painterResource(PlaceholderIconRes),
                contentDescription = "拍照搜索",
                tint = XhsColor.Text2,
                modifier = Modifier.size(Dimens.icon20),
            )
        }

        Box(
            modifier = Modifier
                .width(SearchSubmitWidth)
                .height(Dimens.inputSearch)
                .clip(RoundedCornerShape(Dimens.radiusPill))
                .background(XhsColor.Text1)
                .clickable(onClick = onSubmit),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "搜索", style = XhsType.buttonLabelSmall, color = Color.White)
        }
    }
}

/**
 * B2「历史记录」区块：标题 16sp + 🗑 清空 + 历史 chip（高 32 全圆角、可换行、间距 8）。
 * **历史为空时整块隐藏**（线框 B2 note）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SearchHistorySection(
    history: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (history.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "历史记录",
                style = XhsType.sectionTitle,
                color = XhsColor.Text1,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            // 🗑 清空历史（线框用字符表示该图标；素材缺失，按 §4.5 用统一占位）
            XhsIconButton(
                iconRes = PlaceholderIconRes,
                onClick = onClear,
                iconSize = Dimens.icon20,
                tint = XhsColor.Text2,
                contentDescription = "清空历史记录",
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.s8),
            verticalArrangement = Arrangement.spacedBy(Dimens.s8),
        ) {
            history.forEach { keyword ->
                XhsTextChip(text = keyword, onClick = { onPick(keyword) })
            }
        }
    }
}

/**
 * B2「猜你想搜」：两列，条目行高 22、行距 32（线框实测行距 38，收敛到间距阶梯的 32）。
 * 点击直接搜该词（与历史 chip 一致）。
 */
@Composable
internal fun HotKeywordSection(
    keywords: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (keywords.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "猜你想搜",
            style = XhsType.sectionTitle,
            color = XhsColor.Text1,
            maxLines = 1,
            modifier = Modifier.padding(top = Dimens.s24, bottom = Dimens.s8),
        )

        Column(verticalArrangement = Arrangement.spacedBy(HotKeywordRowGap)) {
            keywords.chunked(2).forEach { rowKeywords ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    rowKeywords.forEach { keyword ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(HotKeywordRowHeight)
                                .clickable { onPick(keyword) },
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = keyword,
                                style = XhsType.searchEntry,
                                color = XhsColor.Text1,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    // 单数行也保持两列宽度
                    if (rowKeywords.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * 瀑布流的「近底部」预加载（B4-4，与 B1 首页同规则）。
 *
 * 与首页同名实现重复的原因：`core/list` 的 `PagedListEffect` 只适配 `LazyListState`，
 * 且本 feature 不得 import 兄弟 feature（§2 边界规则）。已列入「需要改公共组件」建议。
 */
@Composable
internal fun StaggeredLoadMoreEffect(
    gridState: LazyStaggeredGridState,
    enabled: Boolean,
    onLoadMore: () -> Unit,
) {
    val nearBottom by remember(gridState) {
        derivedStateOf {
            val layout = gridState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index
                ?: return@derivedStateOf false
            lastVisible >= layout.totalItemsCount - 1 - PRELOAD_ITEM_COUNT
        }
    }

    LaunchedEffect(nearBottom, enabled) {
        if (enabled && nearBottom) onLoadMore()
    }
}

/** 距底预加载项数（线框 B4-4：滚动距底约 600px，按项数近似为 6 项）。 */
private const val PRELOAD_ITEM_COUNT = 6

/**
 * 线框 B2 实测值，Dimens 无对应档位，故就近声明：
 * 搜索行 52、返回图标 22、搜索按钮宽 56、猜你想搜条目行高 22。
 */
private val SearchRowHeight = 52.dp
private val SearchBackIconSize = 22.dp
private val SearchSubmitWidth = 56.dp
private val HotKeywordRowHeight = 22.dp
private val HotKeywordRowGap = Dimens.s32
