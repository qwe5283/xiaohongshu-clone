package com.xiaohongshu.app.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaohongshu.app.R
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.design.XhsType
import com.xiaohongshu.app.core.ui.XhsAvatar
import com.xiaohongshu.app.core.ui.XhsFollowPill
import com.xiaohongshu.app.core.ui.XhsIconButton
import com.xiaohongshu.app.core.ui.XhsTextChip
import com.xiaohongshu.app.core.ui.XhsVerticalDivider
import com.xiaohongshu.app.core.util.Formatters
import com.xiaohongshu.app.domain.model.UserBrief

/** B2 输入框占位文案（定稿原文）。 */
private const val SearchPlaceholder = "搜索你感兴趣的内容"

/**
 * B3-1 结果筛选页签（契约变更 #17/#18）：
 * - [ALL]「全部」= 图文 + 视频笔记（不传 `type`）；
 * - [VIDEO]「视频」= 仅视频笔记（`type=1`）；
 * - [USER]「用户」= 独立用户列表流（`GET /api/user/search`），故其余两档走瀑布流。
 */
enum class SearchResultFilter(val label: String) {
    ALL("全部"),
    USER("用户"),
    VIDEO("视频"),
}

/**
 * B2 / B3-1 共用的搜索行，外层 Column 承载两行：
 *
 * - 首行高 52：返回 22 → 输入框 44（右侧竖分隔 + 相机入口）→「搜索」按钮 56×44。
 *   输入框回车（IME Search）与「搜索」按钮等价：二者都走 [onSubmit]。
 * - 次行筛选页签（[ResultFilterRow]）：仅搜索结果页传入 [filter] 时显示，
 *   B2 搜索页保持 [filter] = null，不占位。
 */
@Composable
internal fun SearchInputRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    filter: SearchResultFilter? = null,
    onFilterSelect: (SearchResultFilter) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SearchRowHeight)
                .padding(end = Dimens.pagePadding),
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
                    .clip(RoundedCornerShape(Dimens.radiusSearchBar))
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


                // 相机入口（原版「拍照搜索」）：本复刻未纳入接口契约，故仅作视觉入口；
                Icon(
                    painter = painterResource(R.drawable.ic_camera),
                    contentDescription = "拍照搜索",
                    tint = XhsColor.Text2,
                    modifier = Modifier.size(Dimens.icon20),
                )

                XhsVerticalDivider(height = Dimens.s16)

                Box(
                    modifier = Modifier
                        .width(SearchSubmitWidth)
                        .height(Dimens.inputSearch)
                        .clip(RoundedCornerShape(Dimens.radiusPill))
                        .clickable(onClick = onSubmit),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "搜索", style = XhsType.searchEntry, color = XhsColor.Text1)
                }
            }

        }

        if (filter != null) {
            ResultFilterRow(
                selected = filter,
                onSelect = onFilterSelect,
            )
        }
    }
}

/**
 * B3-1 搜索结果页第二行筛选页签：行高/间距/标签样式仿首页频道栏（`feature/home` 的 ChannelBar）。
 *
 * 与频道栏的差异：选项固定为 [SearchResultFilter] 三项、永不溢出，故省去
 * 频道栏的横滑 + 右端「渐隐 + 箭头」暗示；下划线取首页顶栏页签的约定——
 * 选中项红色、未选中透明（频道栏恒为透明）。
 */
@Composable
private fun ResultFilterRow(
    selected: SearchResultFilter,
    onSelect: (SearchResultFilter) -> Unit,
    modifier: Modifier = Modifier.padding(bottom = Dimens.s4),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.channelBar)
            .padding(start = Dimens.s8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.s12),
    ) {
        SearchResultFilter.entries.forEach { option ->
            val isSelected = option == selected
            UnderlineLabel(
                text = option.label,
                textStyle = if (isSelected) XhsType.tabSelected else XhsType.tabUnselected,
                contentColor = if (isSelected) XhsColor.Text1 else XhsColor.Text2,
                underlineColor = if (isSelected) XhsColor.Red else Color.Transparent,
                onClick = { onSelect(option) },
            )
        }
    }
}

/**
 * 「文本 + 2dp 下划线」页签，与 `feature/home/HomeBars.kt` 的同名组件一致：
 * 下划线用 drawBehind 画在文字自身尺寸之下（宽度取 [Dimens.topBarTabUnderlineWidth]，
 * 无需另行测量），外层 Box 补足 ≥44dp 触控热区；想隐藏下划线时 [underlineColor] 传透明值。
 *
 * 重复实现的原因同 [StaggeredLoadMoreEffect]：home 侧为私有组件，跨 feature 复用受 §2 边界规则限制。
 */
@Composable
private fun UnderlineLabel(
    text: String,
    textStyle: TextStyle,
    contentColor: Color,
    underlineColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .defaultMinSize(minWidth = Dimens.minTouchTarget, minHeight = Dimens.minTouchTarget)
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = textStyle,
            color = contentColor,
            maxLines = 1,
            modifier = Modifier.drawBehind {
                val underlineHeight = TabUnderlineHeight.toPx()
                val underlineWidth = Dimens.topBarTabUnderlineWidth.toPx()
                drawRoundRect(
                    color = underlineColor,
                    topLeft = Offset(
                        x = (size.width - underlineWidth) / 2f,
                        y = size.height + TabUnderlineGap.toPx(),
                    ),
                    size = Size(width = underlineWidth, height = underlineHeight),
                    cornerRadius = CornerRadius(underlineHeight / 2f), // 高度一半 = 完美体育场形
                )
            },
        )
    }
}

/**
 * B3-1「用户」页签的行（仿 `feature/message/NotificationComponents.kt` 的 G4 关注条目）：
 * 头像 48 @16 → 昵称 15sp → 粉丝数 13sp → 小红书号 13sp → 右侧关注钮；整行点击 → F2。
 *
 * 关注钮用公共的 [XhsFollowPill]（同一 D2 状态机；样式取 C1-1 图文详情顶栏的
 * 描边胶囊：红字 + 透明底 + 2px 描边，见 [FollowPillSlot]）；
 * [showFollow] = false（命中自己）时不渲染关注钮。
 */
@Composable
internal fun SearchUserRow(
    user: UserBrief,
    followed: Boolean,
    showFollow: Boolean,
    onRowClick: () -> Unit,
    onFollowToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(XhsColor.Bg)
            .clickable(onClick = onRowClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.pagePadding, vertical = Dimens.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            XhsAvatar(url = user.avatar, size = Dimens.avatarConversation)

            Spacer(modifier = Modifier.width(Dimens.s12))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.nickname,
                    style = XhsType.listTitle,
                    color = XhsColor.Text1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (user.followersCount > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "粉丝 ${Formatters.formatStatCount(user.followersCount)}",
                        style = XhsType.cardFooter,
                        color = XhsColor.Text2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "小红书号：${user.displayRedId}",
                    style = XhsType.cardFooter,
                    color = XhsColor.Text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (showFollow) {
                Spacer(modifier = Modifier.width(Dimens.s12))
                FollowPillSlot(followed = followed, onToggle = onFollowToggle)
            }
        }

    }
}

/**
 * 关注钮外面套一层 ≥44 的点击盒并**消费**点击，避免点「关注」时误触整行跳转
 * （与 G4 `FollowBackPill` 同款结构：`XhsFollowPill` 自身高 28，热区不足 44）。
 */
@Composable
private fun FollowPillSlot(
    followed: Boolean,
    onToggle: () -> Unit,
) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = Dimens.minTouchTarget, minHeight = Dimens.minTouchTarget)
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        // 描边胶囊样式与 C1-1 图文详情顶栏关注钮完全同款（红字 + 透明底 + 2px 描边）
        XhsFollowPill(
            followed = followed,
            onToggle = onToggle,
            followFg = XhsColor.Red,
            followBg = Color.Transparent,
            followedBg = Color.Transparent,
            borderWidthPx = 2f,
        )
    }
}

/**
 * B2「历史记录」区块：标题 16sp + 🗑 清空 + 历史 chip（高 32 全圆角、可换行、间距 8）。
 * **历史为空时整块隐藏**。
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
            // 🗑 清空历史
            XhsIconButton(
                iconRes = R.drawable.ic_delete,
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
 * B2「猜你想搜」：两列，条目行高 22、行距收敛到间距阶梯的 32。
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
            modifier = Modifier.padding(top = Dimens.s24, bottom = Dimens.s16),
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

/** 距底预加载项数（B4-4：滚动距底约 600px，按项数近似为 6 项）。 */
private const val PRELOAD_ITEM_COUNT = 6

/**
 * B2 实测值，Dimens 无对应档位，故就近声明：
 * 搜索行 52、返回图标 22、搜索按钮宽 42、猜你想搜条目行高 22。
 */
private val SearchRowHeight = 52.dp
private val SearchBackIconSize = 22.dp
private val SearchSubmitWidth = 42.dp
private val HotKeywordRowHeight = 22.dp
private val HotKeywordRowGap = Dimens.s16

/** 筛选页签下划线：厚 2、绘于文字底边下方 4（与首页顶栏页签同款）。 */
private val TabUnderlineHeight = 2.dp
private val TabUnderlineGap = Dimens.s4
