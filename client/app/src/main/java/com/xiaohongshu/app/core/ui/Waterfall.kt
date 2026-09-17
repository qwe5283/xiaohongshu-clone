package com.xiaohongshu.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaohongshu.app.R
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.design.XhsType
import com.xiaohongshu.app.core.util.Formatters
import com.xiaohongshu.app.domain.model.Note

/**
 * 瀑布流卡片（线框 B1 / T-4c 实测）。
 *
 * 结构：封面（按真实宽高比 4:3 或 3:4）→ 标题（最多两行）→ 脚栏（头像 24 + 昵称 12sp + ♥ + 计数 12sp）。
 *
 * 关于脚栏 ♥ 尺寸的取值说明：T-3 实测写「赞 icon 30」，但 T-4b 把 30 明确限定为
 * 「详情/视频底栏互动图标」，其余图标收敛为 12/16/20/24 四档。为避免 30dp 图标压过 12sp 计数
 * （原版小红书卡片脚栏的心形约与文字同高），此处取 16dp 档；详情底栏仍用 30dp。
 *
 * 计数为 0 时按线框规则显示文字「赞」（见 [XhsCountText]）。
 */
@Composable
fun WaterfallCard(
    note: Note,
    onNoteClick: (Note) -> Unit,
    onAuthorClick: (Long) -> Unit,
    onLikeClick: (Note) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusCard))
            .background(XhsColor.Bg)
            .clickable { onNoteClick(note) },
    ) {
        // 封面：比例来自首图真实尺寸，视频笔记回落 3:4
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(note.coverRatio)
                .clip(RoundedCornerShape(Dimens.radiusCard)),
        ) {
            XhsAsyncImage(
                url = note.coverUrl.ifBlank { note.images.firstOrNull()?.url.orEmpty() },
                modifier = Modifier.fillMaxWidth(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            if (note.isVideo) {
                // 视频角标 20×20，右上 inset 10（T-4c）
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Dimens.videoBadgeInset)
                        .size(Dimens.videoBadge)
                        .clip(RoundedCornerShape(Dimens.radiusPill))
                        .background(androidx.compose.ui.graphics.Color(0x66000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    // ▶ 圆角实心三角；ic_play 按原 Canvas 字形的占位比例归一化，故尺寸不变
                    Icon(
                        painter = painterResource(R.drawable.ic_play),
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
        }

        // 标题：最多两行，超出截断
        if (note.title.isNotBlank()) {
            Text(
                text = note.title,
                style = XhsType.body,
                color = XhsColor.Text1,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(
                    start = Dimens.cardInnerPadding,
                    end = Dimens.cardInnerPadding,
                    top = Dimens.s8,
                ),
            )
        }

        // 脚栏：高 40 = 上下 6 + 头像 24（T-4c 示例）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = Dimens.cardInnerPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            XhsAvatar(
                url = note.authorAvatar,
                size = Dimens.avatarCard,
                modifier = Modifier.clickable { onAuthorClick(note.authorId) },
            )
            Spacer(modifier = Modifier.width(Dimens.s8))
            Text(
                text = note.authorLabel,
                style = XhsType.cardFooter,
                color = XhsColor.Text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onAuthorClick(note.authorId) },
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.clickable { onLikeClick(note) },
            ) {
                Icon(
                    painter = painterResource(
                        if (note.liked) R.drawable.ic_heart_filled else R.drawable.ic_heart,
                    ),
                    contentDescription = if (note.liked) "取消点赞" else "点赞",
                    tint = if (note.liked) XhsColor.Red else XhsColor.Text2,
                    modifier = Modifier.size(Dimens.icon16),
                )
                Text(
                    text = if (note.likeCount > 0) Formatters.formatCount(note.likeCount) else "赞",
                    style = XhsType.cardFooter,
                    color = XhsColor.Text2,
                    maxLines = 1,
                )
            }
        }
        Spacer(modifier = Modifier.height(Dimens.s4))
    }
}

/**
 * 双列瀑布流列表。
 *
 * 用 `LazyVerticalStaggeredGrid` 而非两列手排，卡片高度随封面比例自然错落，
 *
 * 各页面（B1/B3/B5/F1/F2）共用本组件；页面级差异通过 [header] / [footer] 槽位注入。
 *
 * 现状：[footer] 被全部调用方注入 [XhsListFooter] 作分页页脚（加载中/没有更多了）；[header] 暂无使用方，为「列表顶部随滚动内容」预留。
 */
@Composable
fun PostWaterfall(
    notes: List<Note>,
    listState: LazyStaggeredGridState,
    onNoteClick: (Note) -> Unit,
    onAuthorClick: (Long) -> Unit,
    onLikeClick: (Note) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        start = Dimens.waterfallMargin,
        end = Dimens.waterfallMargin,
        top = Dimens.waterfallMargin,
        bottom = Dimens.waterfallMargin,
    ),
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalItemSpacing = Dimens.waterfallGap,
        horizontalArrangement = Arrangement.spacedBy(Dimens.waterfallGap),
    ) {
        if (header != null) {
            item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                header()
            }
        }
        items(items = notes, key = { it.id }) { note ->
            WaterfallCard(
                note = note,
                onNoteClick = onNoteClick,
                onAuthorClick = onAuthorClick,
                onLikeClick = onLikeClick,
            )
        }
        if (footer != null) {
            item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                footer()
            }
        }
    }
}
