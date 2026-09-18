package com.xiaohongshu.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaohongshu.app.R
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.design.XhsType
import com.xiaohongshu.app.core.ui.PlaceholderIconRes
import com.xiaohongshu.app.core.ui.XhsAsyncImage
import com.xiaohongshu.app.core.ui.XhsAvatar
import com.xiaohongshu.app.core.ui.XhsIconButton
import com.xiaohongshu.app.domain.model.User

/**
 * F1 / F2 共用的资料头（线框 F1 实测：头图 282、浮层顶栏 44、头像 108 @(2,78)、昵称 24sp、
 * 统计行 24、简介行 26、性别 chip 30×20）。
 *
 * 结构说明：头图是**整块 282 的背景图**，顶栏与资料信息浮在它之上（线框 F1/F2 的头部为整块渐变块）；
 * 头像用 [Dimens.profileAvatarOffsetX] / [Dimens.profileAvatarOffsetY] 的实测偏移定位，
 * 与顶栏解耦，顶栏高度变化不会把头像带偏。
 *
 * 差异通过参数表达，而不是复制两份：
 * - [onEditProfile] 非空 = F1（带「编辑主页」pill）；为空 = F2；
 * - [onMoreClick] 非空 = F2（「⋯」视觉占位）；
 * - [pushed] = 推入态（F2 / 评论区点自己头像进入的个人页）：左上角渲染 ← 而非 ☰。
 *   isMe 只管内容形态（编辑 pill、扫一扫/分享、简介引导），与进页方式正交；
 * - 小组件行 / 「去发布」banner 不属于本组件，由 F1 单独渲染。
 */
@Composable
internal fun ProfileHeader(
    user: User,
    isMe: Boolean,
    onLeftAction: () -> Unit,
    onCopyRedId: () -> Unit,
    modifier: Modifier = Modifier,
    pushed: Boolean = false,
    onEditProfile: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
    onBioClick: (() -> Unit)? = null,
    /** 头部内追加内容（F2 的通栏关注按钮），渲染在简介行之后。 */
    footer: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.profileHeaderImage),
    ) {
        // 头图：缺失素材由 XhsAsyncImage 统一占位（线框亦为占位底纹）
        XhsAsyncImage(
            url = user.backgroundImage,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        ProfileFloatingTopBar(
            isMe = isMe,
            pushed = pushed,
            onLeftAction = onLeftAction,
            onEditProfile = onEditProfile,
            onMoreClick = onMoreClick,
            modifier = Modifier.align(Alignment.TopStart),
        )

        // 头像 + 昵称 + 小红书号/IP + 统计行 + 简介行 + 性别 chip（实测偏移 @(2,78)）
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(
                    x = Dimens.profileAvatarOffsetX,
                    y = Dimens.profileAvatarOffsetY,
                ),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                XhsAvatar(url = user.avatar, size = Dimens.avatarProfile)
                Spacer(modifier = Modifier.width(Dimens.s12))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.displayName,
                        style = XhsType.profileNickname,
                        color = XhsColor.Text1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(Dimens.s4))
                    // 小红书号 + 复制（真实写系统剪贴板）+ IP 属地（取自 region 字段）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "小红书号：${user.displayRedId}",
                            style = XhsType.meta,
                            color = XhsColor.Text2,
                            maxLines = 1,
                        )
                        Box(
                            modifier = Modifier
                                .size(Dimens.minTouchTarget)
                                .clickable(onClick = onCopyRedId),
                            contentAlignment = Alignment.Center,
                        ) {
                            // 复制图标为缺失素材（§4.5）：统一占位
                            Icon(
                                painter = painterResource(PlaceholderIconRes),
                                contentDescription = "复制小红书号",
                                tint = XhsColor.Text2,
                                modifier = Modifier.size(Dimens.icon12),
                            )
                        }
                        if (user.region.isNotBlank()) {
                            Text(
                                text = "IP 属地：${user.region}",
                                style = XhsType.meta,
                                color = XhsColor.Text2,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.s8))

            ProfileStatsRow(user = user)

            ProfileBioRow(
                user = user,
                isMe = isMe,
                onBioClick = onBioClick,
            )

            if (footer != null) {
                Spacer(modifier = Modifier.height(Dimens.s4))
                footer()
            }
        }
    }
}

/** 浮层顶栏（线框 F1/F2：高 44，含状态栏内边距，浮在头图上）。 */
@Composable
private fun ProfileFloatingTopBar(
    isMe: Boolean,
    pushed: Boolean,
    onLeftAction: () -> Unit,
    onEditProfile: (() -> Unit)?,
    onMoreClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.topBar),
        ) {
            // 左：F1 ☰（开抽屉）/ 推入态 ←（返回）。推入的自个人页是「← + isMe 内容」，
            // 故 ☰ 只在「我 + Tab 根页面」时出现
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = Dimens.s4)
                    .size(Dimens.minTouchTarget)
                    .clickable(onClick = onLeftAction),
                contentAlignment = Alignment.Center,
            ) {
                if (isMe && !pushed) {
                    Icon(
                        painter = painterResource(R.drawable.ic_menu),
                        contentDescription = "菜单",
                        tint = XhsColor.Text1,
                        modifier = Modifier.size(Dimens.profileMenuIcon),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_left),
                        contentDescription = "返回",
                        tint = XhsColor.Text1,
                        modifier = Modifier.size(Dimens.icon24),
                    )
                }
            }

            if (onEditProfile != null) {
                EditProfilePill(
                    onClick = onEditProfile,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = Dimens.s4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isMe) {
                    // 扫一扫 / 分享：线框明确为**视觉占位**（无行为），但素材已就位
                    XhsIconButton(
                        iconRes = R.drawable.ic_scan,
                        onClick = {},
                        tint = XhsColor.Text1,
                        iconSize = Dimens.icon24,
                        contentDescription = "扫一扫",
                    )
                    Spacer(modifier = Modifier.width(Dimens.profileTopIconGap))
                    XhsIconButton(
                        iconRes = R.drawable.ic_share,
                        onClick = {},
                        tint = XhsColor.Text1,
                        iconSize = Dimens.icon24,
                        contentDescription = "分享",
                    )
                } else if (onMoreClick != null) {
                    // F2「⋯」：线框明确为视觉占位
                    XhsIconButton(
                        iconRes = PlaceholderIconRes,
                        onClick = onMoreClick,
                        tint = XhsColor.Text1,
                        iconSize = Dimens.icon24,
                        contentDescription = "更多",
                    )
                }
            }
        }
    }
}

/** 「编辑主页」pill（线框 F1：93×26、icon 18）。 */
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

/**
 * 统计行（高 24）：关注 / 粉丝 / **获赞与收藏**。
 *
 * 第三项取 `user.likeAndCollectCount`（**收到**的赞与收藏总数）；
 * `likeCount` / `collectCount` 是同义旧字段、`collectedPostCount` / `likedPostCount` 是
 * 「我赞过/收藏过」的**相反**语义，三者都不能用在这里（契约 §0 语义说明）。
 */
@Composable
private fun ProfileStatsRow(user: User) {
    Row(
        modifier = Modifier.height(Dimens.profileStatsRow),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatItem(count = user.followingCount, label = "关注")
        Spacer(modifier = Modifier.width(Dimens.s12))
        StatItem(count = user.followersCount, label = "粉丝")
        Spacer(modifier = Modifier.width(Dimens.s12))
        StatItem(count = user.likeAndCollectCount, label = "获赞与收藏")
    }
}

@Composable
private fun StatItem(count: Long, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = statText(count),
            style = XhsType.s(14, emphasis = true),
            color = XhsColor.Text1,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(Dimens.s4))
        Text(
            text = label,
            style = XhsType.meta,
            color = XhsColor.Text2,
            maxLines = 1,
        )
    }
}

/**
 * 简介行（高 26）+ 性别 chip（30×20，icon 12）。
 *
 * F1：简介为空显示引导文案「点击这里，填写简介」（线框 F1）；
 * F2：他人简介为空时整行隐藏（不显示引导文案）；性别未知（`gender == 0`，含「保密」）不显示图标。
 */
@Composable
private fun ProfileBioRow(
    user: User,
    isMe: Boolean,
    onBioClick: (() -> Unit)?,
) {
    val bio = user.bio.trim()
    val guidance = isMe && bio.isEmpty()
    if (bio.isEmpty() && !user.hasGender && !guidance) return

    Row(
        modifier = Modifier.height(Dimens.profileBioRow),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (bio.isNotEmpty() || guidance) {
            Text(
                text = if (guidance) "点击这里，填写简介" else bio,
                style = XhsType.meta,
                color = if (guidance) XhsColor.Text2 else XhsColor.Text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (onBioClick != null) {
                    Modifier.clickable(onClick = onBioClick)
                } else {
                    Modifier
                },
            )
        }
        if (user.hasGender) {
            Spacer(modifier = Modifier.width(Dimens.s8))
            GenderChip(gender = user.gender)
        }
    }
}

/** 性别 chip（线框 F1：30×20、icon 12；只显示男/女，「保密」= 未设置故不显示）。 */
@Composable
private fun GenderChip(gender: Int) {
    Box(
        modifier = Modifier
            .width(Dimens.genderChipWidth)
            .height(Dimens.genderChipHeight)
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .background(XhsColor.BgGray),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(
                if (gender == GENDER_FEMALE) R.drawable.ic_female else R.drawable.ic_male,
            ),
            contentDescription = null,
            tint = XhsColor.Text1,
            modifier = Modifier.size(Dimens.icon12),
        )
    }
}

/** 性别取值（契约 §1.5：0-未知/保密、1-男、2-女）。 */
internal const val GENDER_UNKNOWN = 0
internal const val GENDER_MALE = 1
internal const val GENDER_FEMALE = 2
