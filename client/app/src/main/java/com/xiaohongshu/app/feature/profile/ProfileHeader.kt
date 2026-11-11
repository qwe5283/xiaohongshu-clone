package com.xiaohongshu.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import com.xiaohongshu.app.R
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.design.XhsType
import com.xiaohongshu.app.core.ui.XhsAsyncImage
import com.xiaohongshu.app.core.ui.XhsAvatar
import com.xiaohongshu.app.domain.model.User

@Composable
internal fun ProfileHeader(
    user: User,
    isMe: Boolean,
    onCopyRedId: () -> Unit,
    onAvatarBottomChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onBioClick: (() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    val headerCoordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.profileHeaderImage)
            .onGloballyPositioned { headerCoordinates.value = it },
    ) {
        XhsAsyncImage(
            url = user.backgroundImage,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(XhsColor.ProfileHeaderScrim),
        )

        // 契约 §12.8：顶部留白必须参与测量，独立性别行与访客操作行才能撑开资料头。
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = Dimens.profileAvatarOffsetY)
                .offset(x = Dimens.profileAvatarOffsetX)
                .padding(Dimens.pagePadding),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                XhsAvatar(
                    url = user.avatar,
                    size = Dimens.avatarProfile,
                    modifier = Modifier.onGloballyPositioned { avatar ->
                        val header = headerCoordinates.value
                        if (header != null && header.isAttached) {
                            onAvatarBottomChanged(header.localPositionOf(avatar, Offset.Zero).y + avatar.size.height)
                        }
                    },
                )
                Spacer(modifier = Modifier.width(Dimens.s12))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.displayName,
                        style = XhsType.profileNickname,
                        color = XhsColor.Text1Dark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(Dimens.s4))
                    // 小红书号，点击复制
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "小红书号：${user.displayRedId}",
                            style = XhsType.meta,
                            color = XhsColor.Text2Dark,
                            maxLines = 1,
                            modifier = Modifier.clickable(onClick = onCopyRedId),
                        )
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

            if (user.hasGender) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.s4),
                ) {
                    GenderChip(gender = user.gender)
                }
            }

            if (footer != null) {
                Spacer(modifier = Modifier.height(Dimens.s8))
                footer()
            }
        }
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
            color = XhsColor.Text1Dark,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(Dimens.s4))
        Text(
            text = label,
            style = XhsType.meta,
            color = XhsColor.Text2Dark,
            maxLines = 1,
        )
    }
}

/** 契约 §12.8：简介引导只对本人有意义，不能按 F1/F2 入口区分。 */
@Composable
private fun ProfileBioRow(
    user: User,
    isMe: Boolean,
    onBioClick: (() -> Unit)?,
) {
    val bio = user.bio.trim()
    val guidance = isMe && bio.isEmpty()
    if (bio.isEmpty() && !guidance) return

    Row(
        modifier = Modifier.height(Dimens.profileBioRow),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (guidance) "点击这里，填写简介" else bio,
            style = XhsType.meta,
            color = XhsColor.Text1Dark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (onBioClick != null) {
                Modifier.clickable(onClick = onBioClick)
            } else {
                Modifier
            },
        )
    }
}

/** 性别 chip（30×20、icon 12；只显示男/女，「保密」= 未设置故不显示）。 */
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
