package com.xiaohongshu.app.feature.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import com.xiaohongshu.app.R
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.design.XhsType
import com.xiaohongshu.app.core.ui.XhsAvatar
import com.xiaohongshu.app.core.ui.XhsIconButton
import com.xiaohongshu.app.domain.model.User

@Composable
internal fun ProfileFloatingTopBar(
    user: User,
    collapseState: ProfileCollapseState,
    backgroundColor: Color,
    isMe: Boolean,
    presentation: ProfilePresentation,
    onLeftAction: () -> Unit,
    onEditProfile: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val showAvatar by remember(collapseState) { derivedStateOf { collapseState.showTopBarAvatar } }
    val avatarTravel = with(LocalDensity.current) {
        ((Dimens.topBar + Dimens.avatarDetailAuthor) / 2).roundToPx()
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind { drawRect(backgroundColor.copy(alpha = collapseState.topBarAlpha)) }
            // 契约 §12.8：顶栏遮住的资料操作不可被穿透点击。
            .pointerInput(Unit) { detectTapGestures(onTap = {}) }
            .statusBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.topBar)
                .clipToBounds(),
        ) {
            AnimatedVisibility(
                visible = showAvatar,
                modifier = Modifier.align(Alignment.Center),
                enter = slideInVertically(initialOffsetY = { avatarTravel }),
                exit = slideOutVertically(targetOffsetY = { avatarTravel }),
            ) {
                XhsAvatar(url = user.avatar, size = Dimens.avatarDetailAuthor)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = Dimens.s4)
                    .size(Dimens.minTouchTarget)
                    .clickable(onClick = onLeftAction),
                contentAlignment = Alignment.Center,
            ) {
                if (presentation == ProfilePresentation.TAB_ROOT) {
                    Icon(
                        painter = painterResource(R.drawable.ic_menu),
                        contentDescription = "菜单",
                        tint = XhsColor.Text1Dark,
                        modifier = Modifier.size(Dimens.profileMenuIcon),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_left),
                        contentDescription = "返回",
                        tint = XhsColor.Text1Dark,
                        modifier = Modifier.size(Dimens.icon24),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = Dimens.s4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isMe) {
                    if (onEditProfile != null && !showAvatar) {
                        EditProfilePill(
                            onClick = onEditProfile,
                        )
                        Spacer(modifier = Modifier.width(Dimens.profileTopIconGap))
                    }
                    // 扫一扫 / 分享：**视觉占位**（无行为），但素材已就位
                    XhsIconButton(
                        iconRes = R.drawable.ic_scan,
                        onClick = {},
                        tint = XhsColor.Text1Dark,
                        iconSize = Dimens.icon24,
                        contentDescription = "扫一扫",
                    )
                }
                XhsIconButton(
                    iconRes = R.drawable.ic_share,
                    onClick = {},
                    tint = XhsColor.Text1Dark,
                    iconSize = Dimens.icon24,
                    contentDescription = "分享",
                )
            }
        }
    }
}

/** 「编辑主页」pill（93×26、icon 18）。 */
@Composable
private fun EditProfilePill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .width(Dimens.profileEditPillWidth)
            .height(Dimens.profileEditPillHeight)
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .background(XhsColor.Bg)
            .border(
                width = Dimens.hairline,
                color = XhsColor.Divider,
                shape = RoundedCornerShape(Dimens.radiusPill),
            )
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // 铅笔图标；18dp 取 Dimens 中唯一的同档 token（segmentIcon = 18）
        Icon(
            painter = painterResource(R.drawable.ic_edit),
            contentDescription = null,
            tint = XhsColor.Text1,
            modifier = Modifier.size(Dimens.segmentIcon),
        )
        Spacer(modifier = Modifier.width(Dimens.s4))
        Text(
            text = "编辑主页",
            style = XhsType.meta,
            color = XhsColor.Text1,
            maxLines = 1,
        )
    }
}

