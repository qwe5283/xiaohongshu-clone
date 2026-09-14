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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.xiaohongshu.app.R
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.design.XhsType
import com.xiaohongshu.app.core.ui.PlaceholderIconRes
import com.xiaohongshu.app.core.ui.XhsDivider
import com.xiaohongshu.app.core.ui.XhsTopBar

/**
 * F5 设置（无状态；路由见 [SettingsRoute]）。
 *
 * 线框 T-3 实测：列表行距 52（label 15sp @68、icon 24 @28、右值灰如「2.25 GB」）；
 * 分组 = 账号与安全/通用设置/通知设置/多语言和翻译/隐私设置｜存储空间/内容偏好调节/收货地址/
 * 添加小组件/未成年人模式｜新功能体验｜帮助与客服/关于小红书；底部居中「切换账号」15sp。
 *
 * **子页全部为占位**（线框 F5：范围待产品定义），故除「退出登录」外一律无跳转；
 * 行图标除「关于小红书」用真实素材 `ic_about` 外，其余为缺失素材 → `ic_placeholder`（§4.5）。
 */
@Composable
internal fun SettingsScreen(
    onBack: () -> Unit,
    onLogoutClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(XhsColor.Bg),
    ) {
        XhsTopBar(title = "设置", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsGroup {
                SettingRow(label = "账号与安全")
                XhsDivider()
                SettingRow(label = "通用设置")
                XhsDivider()
                SettingRow(label = "通知设置")
                XhsDivider()
                SettingRow(label = "多语言和翻译")
                XhsDivider()
                SettingRow(label = "隐私设置")
            }

            SettingsGroupGap()

            SettingsGroup {
                // 「2.25 GB」为线框示例的占位数值（本地未接存储统计）
                SettingRow(label = "存储空间", value = "2.25 GB")
                XhsDivider()
                SettingRow(label = "内容偏好调节")
                XhsDivider()
                SettingRow(label = "收货地址")
                XhsDivider()
                SettingRow(label = "添加小组件")
                XhsDivider()
                SettingRow(label = "未成年人模式")
            }

            SettingsGroupGap()

            SettingsGroup {
                SettingRow(label = "新功能体验")
            }

            SettingsGroupGap()

            SettingsGroup {
                SettingRow(label = "帮助与客服")
                XhsDivider()
                SettingRow(label = "关于小红书", iconRes = R.drawable.ic_about)
            }

            Spacer(modifier = Modifier.height(Dimens.s24))

            // 底部：居中「切换账号」15sp（占位，无行为）+ 「退出登录」→ F6
            SettingsFooterRow(label = "切换账号", onClick = null)
            SettingsFooterRow(
                label = "退出登录",
                onClick = onLogoutClick,
                emphasized = true,
            )

            Spacer(modifier = Modifier.height(Dimens.s24))
        }
    }
}

/** 设置分组（一组行）。 */
@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s8)) { content() }
}

/** 分组间隔（线框 T-3 的分组视觉分隔）。 */
@Composable
private fun SettingsGroupGap() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.s8)
            .background(XhsColor.BgGray),
    )
}

/**
 * 设置行：高 52，icon 24 @28、label 15sp @68，右侧灰色值 + 进入箭头。
 * 子页均为占位 → [onClick] 为空时不响应点击（仅展示）。
 */
@Composable
private fun SettingRow(
    label: String,
    iconRes: Int = PlaceholderIconRes,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    height: Dp = Dimens.rowSetting,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(Dimens.settingIconStart))
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = XhsColor.Text2,
            modifier = Modifier.size(Dimens.icon24),
        )
        // icon 终点 28 + 24 = 52，再空 16 → label @68（实测）
        Spacer(modifier = Modifier.width(Dimens.s16))
        Text(
            text = label,
            style = XhsType.settingRow,
            color = XhsColor.Text1,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (!value.isNullOrBlank()) {
            Text(
                text = value,
                style = XhsType.settingRow,
                color = XhsColor.Text2,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.width(Dimens.s8))
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = XhsColor.Text3,
            modifier = Modifier.size(Dimens.icon16),
        )
        Spacer(modifier = Modifier.width(Dimens.pagePadding))
    }
}

/** 底部居中行（「切换账号」/「退出登录」）。 */
@Composable
private fun SettingsFooterRow(
    label: String,
    onClick: (() -> Unit)?,
    emphasized: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.rowSetting)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = XhsType.s(15, emphasis = emphasized),
            color = XhsColor.Text1,
            maxLines = 1,
        )
    }
}
