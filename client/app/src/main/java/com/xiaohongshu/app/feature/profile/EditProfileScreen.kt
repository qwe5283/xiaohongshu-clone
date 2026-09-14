package com.xiaohongshu.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.xiaohongshu.app.R
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.design.XhsType
import com.xiaohongshu.app.core.ui.PlaceholderIconRes
import com.xiaohongshu.app.core.ui.XhsAsyncImage
import com.xiaohongshu.app.core.ui.XhsAvatar
import com.xiaohongshu.app.core.ui.XhsDivider
import com.xiaohongshu.app.core.ui.XhsTextAction
import com.xiaohongshu.app.core.ui.XhsTopBar

/** F3 编辑资料内容层（无状态；路由与 F3-1/F3-2 弹层见 [EditProfileRoute]）。 */
@Composable
internal fun EditProfileScreen(
    state: EditProfileUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onFieldClick: (EditField) -> Unit,
    onGenderClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onBackgroundClick: () -> Unit,
) {
    val draft = state.draft

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(XhsColor.Bg),
    ) {
        XhsTopBar(
            title = "编辑资料",
            onBack = onBack,
            actions = {
                // 线框 F3：提交中「保存中...」禁用且不可返回
                XhsTextAction(
                    text = if (state.saving) "保存中..." else "保存",
                    enabled = !state.saving,
                    onClick = onSave,
                )
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            // 头像（线框 F3：列表上方的居中头像块；点击走系统相册并即时预览）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimens.s16),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.clickable(onClick = onAvatarClick)) {
                    if (draft.avatarLocalPath != null) {
                        LocalImagePreview(
                            path = draft.avatarLocalPath,
                            modifier = Modifier
                                .size(Dimens.avatarProfile)
                                .clip(CircleShape),
                        )
                    } else {
                        XhsAvatar(url = state.user.avatar, size = Dimens.avatarProfile)
                    }
                    // 相机角标（素材缺失 → §4.5 统一占位）
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(Dimens.avatarCard)
                            .clip(RoundedCornerShape(Dimens.radiusPill))
                            .background(XhsColor.Bg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(PlaceholderIconRes),
                            contentDescription = "更换头像",
                            tint = XhsColor.Text2,
                            modifier = Modifier.size(Dimens.icon16),
                        )
                    }
                }
            }

            // 名字 / 小红书号(不可编辑) / 背景图
            EditRow(
                label = "名字",
                onClick = { onFieldClick(EditField.NAME) },
                value = {
                    RowValueText(text = draft.nickname, placeholder = EditField.NAME.placeholder)
                },
            )
            XhsDivider()
            EditRow(
                label = "小红书号",
                onClick = null,
                showChevron = false,
                value = { RowValueText(text = state.user.displayRedId, placeholder = null) },
            )
            XhsDivider()
            EditRow(
                label = "背景图",
                rowHeight = Dimens.rowEditBackground,
                onClick = onBackgroundClick,
                value = {
                    if (draft.backgroundLocalPath != null) {
                        LocalImagePreview(
                            path = draft.backgroundLocalPath,
                            modifier = Modifier
                                .size(Dimens.rowEditBackground)
                                .clip(RoundedCornerShape(Dimens.radiusCard)),
                        )
                    } else {
                        XhsAsyncImage(
                            url = state.user.backgroundImage,
                            modifier = Modifier
                                .size(Dimens.rowEditBackground)
                                .clip(RoundedCornerShape(Dimens.radiusCard)),
                        )
                    }
                },
            )
            XhsDivider()

            // 简介
            EditRow(
                label = "简介",
                onClick = { onFieldClick(EditField.BIO) },
                value = {
                    RowValueText(text = draft.bio, placeholder = EditField.BIO.placeholder)
                },
            )
            XhsDivider()

            // 性别 / 生日 / 地区 / 职业 / 学校
            EditRow(
                label = "性别",
                onClick = onGenderClick,
                value = { RowValueText(text = draft.genderLabel, placeholder = null) },
            )
            XhsDivider()
            EditRow(
                label = "生日",
                onClick = { onFieldClick(EditField.BIRTHDAY) },
                value = {
                    RowValueText(
                        text = draft.birthday,
                        placeholder = EditField.BIRTHDAY.placeholder,
                    )
                },
            )
            XhsDivider()
            EditRow(
                label = "地区",
                onClick = { onFieldClick(EditField.REGION) },
                value = {
                    RowValueText(text = draft.region, placeholder = EditField.REGION.placeholder)
                },
            )
            XhsDivider()
            EditRow(
                label = "职业",
                onClick = { onFieldClick(EditField.OCCUPATION) },
                value = {
                    RowValueText(
                        text = draft.occupation,
                        placeholder = EditField.OCCUPATION.placeholder,
                    )
                },
            )
            XhsDivider()
            EditRow(
                label = "学校",
                onClick = { onFieldClick(EditField.SCHOOL) },
                value = {
                    RowValueText(text = draft.school, placeholder = EditField.SCHOOL.placeholder)
                },
            )

            Spacer(modifier = Modifier.height(Dimens.s32))
        }
    }
}

/**
 * F3 表单行：行高 48（背景图行 40）、label 宽 89 @32、值 @137（线框 T-3 实测）。
 * 值区右侧为进入箭头；「小红书号」行不可点击且无箭头。
 */
@Composable
private fun EditRow(
    label: String,
    onClick: (() -> Unit)?,
    value: @Composable () -> Unit,
    rowHeight: Dp = Dimens.rowEditProfile,
    showChevron: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(Dimens.editLabelStart))
        Text(
            text = label,
            style = XhsType.s(15),
            color = XhsColor.Text1,
            maxLines = 1,
            modifier = Modifier.width(Dimens.editLabelWidth),
        )
        // label 终点 = 32 + 89 = 121，再空 16 → 值列起于 137（实测 @137.1）
        Spacer(modifier = Modifier.width(Dimens.s16))
        Box(modifier = Modifier.weight(1f)) { value() }
        if (showChevron) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = XhsColor.Text3,
                modifier = Modifier.size(Dimens.icon16),
            )
        }
        Spacer(modifier = Modifier.width(Dimens.s16))
    }
}

/** 行的值文本：空值显示占位文案（弱色），有值用主色。 */
@Composable
private fun RowValueText(text: String, placeholder: String?) {
    val empty = text.isBlank()
    Text(
        text = if (empty) placeholder.orEmpty() else text,
        style = XhsType.s(15),
        color = if (empty) XhsColor.Text3 else XhsColor.Text1,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
