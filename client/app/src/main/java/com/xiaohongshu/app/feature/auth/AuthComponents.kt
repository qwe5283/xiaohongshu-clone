package com.xiaohongshu.app.feature.auth

/**
 * A3/A4 共用的表单控件与文案。
 *
 * 为什么不放进 `core/ui`：它们只被 auth 一个 feature 使用（规范 §4.8「新增公共组件的门槛：
 * 两个以上 feature 会用才放 core/，否则留在自己的 feature 目录里」）。样式与 `core/ui` 里的
 * 同名控件一致（颜色/字号/尺寸全部取自 `core/design`，无魔法数）。
 */

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.xiaohongshu.app.R
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.design.XhsType
import com.xiaohongshu.app.core.ui.XhsCheckCircle

// ================================================================ 文案（线框原文为准）

/** A3 标语（线框 A3/A5-1/A5-2 原文）。 */
internal const val LoginTagline = "登录后，体验更多功能"

/** A3 顶栏右上「帮助」（线框原文）。 */
internal const val HelpLabel = "帮助"

/**
 * 「帮助」是线框标记的 `#9` 占位（线框 A3：「协议链接为占位（#9）」，帮助同为占位）——
 * **不自建页面**：点击只弹全局 Toast（I2），不改导航、不做假页面。
 */
internal const val HelpPlaceholderToast = "帮助内容暂未提供"

/** A6 定稿文案（线框 A6 / 规范 §7 A6）。 */
internal const val LoginSuccessToast = "登录成功"

/** A4 定稿文案（线框 A4 / 规范 §7 A4）：注册成功**不自动登录**。 */
internal const val RegisterSuccessToast = "注册成功，请登录"

/** A3 用户名占位（线框 A3 原文），同时用作「未填写」时的错误条文案。 */
internal const val UsernamePlaceholder = "请输入用户名"

/** A3 密码占位（线框 A3 原文），同时用作「未填写」时的错误条文案。 */
internal const val PasswordPlaceholder = "请输入密码"

/** A3 提交按钮文案（线框 A3 原文）。 */
internal const val LoginLabel = "登录"

/** A5-2 提交中按钮文案（线框 A5-2 原文）。 */
internal const val LoginSubmitLabel = "登录中..."

/** A4 提交按钮文案（线框 A4 原文）。 */
internal const val RegisterLabel = "注册"

/** A5-2 注册提交中按钮文案（线框 A5-2 口径：「登录中...」的注册版）。 */
internal const val RegisterSubmitLabel = "注册中..."

// ================================================================ 控件

/**
 * A3/A4 输入框（线框 `.inp`：白底、1px 描边、圆角）。
 *
 * - 高 = [Dimens.minTouchTarget]（44，触控热区一档；线框为低保真示意值，不照抄）；
 * - 描边 = `Dimens.hairline` + `XhsColor.Text3`，圆角 = `Dimens.radiusCard`；
 * - 占位 = `XhsColor.Text3`（§4.1「弱文字/占位」）；
 * - [password] = true 时用 [PasswordVisualTransformation] 掩码（A3 密码框）；
 * - 提交中（A5-2）用 [enabled] = false 锁住内容，避免请求在途时被改写。
 */
@Composable
internal fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.minTouchTarget)
            .clip(RoundedCornerShape(Dimens.radiusCard))
            .background(XhsColor.Bg)
            .border(
                width = Dimens.hairline,
                color = XhsColor.Text3,
                shape = RoundedCornerShape(Dimens.radiusCard),
            )
            .padding(horizontal = Dimens.s12),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(text = placeholder, style = XhsType.inputPlaceholder, color = XhsColor.Text3)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = XhsType.inputPlaceholder.copy(color = XhsColor.Text1),
            cursorBrush = SolidColor(XhsColor.Text1),
            visualTransformation = if (password) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * A3/A4 居中 LOGO + 标语（线框 A3：LOGO 居中，下方「登录后，体验更多功能」）。
 *
 * LOGO 用真实素材 `R.drawable.ic_logo`（非占位）；高度取 `Dimens.s32`，宽按素材
 * 205×96 等比 ≈ 68dp——线框画的是 72×30 的占位框，两者一致。
 */
@Composable
internal fun AuthLogoHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = "小红书",
            modifier = Modifier.height(Dimens.s32),
        )
        Spacer(modifier = Modifier.height(Dimens.s8))
        Text(text = LoginTagline, style = XhsType.meta, color = XhsColor.Text2)
    }
}

/** 协议文案里的两个书名号链接（线框 A3/A4 原文，`#9` 占位）。 */
private const val AgreementPrefix = "我已阅读并同意"
private const val AgreementUserDoc = "《用户协议》"
private const val AgreementConjunction = "和"
private const val AgreementPrivacyDoc = "《隐私政策》"

/**
 * A3/A4 协议勾选行（线框：小圆圈 + 「我已阅读并同意《用户协议》和《隐私政策》」）。
 *
 * - 圆圈用结构化字形 [XhsCheckCircle]（§4.5：不要用占位素材替代），[checked] 为 true 时红底白勾；
 * - 整行（高 44 = 触控热区下限）可点 = 切换勾选；
 * - 「《用户协议》/《隐私政策》」是线框标记的 `#9` 占位，**不跳转**：只做下划线样式，
 *   点击行为与整行一致（即切换勾选），全项目不为它们建页面。
 */
@Composable
internal fun AuthAgreementRow(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val label = remember {
        buildAnnotatedString {
            append(AgreementPrefix)
            withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                append(AgreementUserDoc)
            }
            append(AgreementConjunction)
            withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                append(AgreementPrivacyDoc)
            }
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.minTouchTarget)
            .clickable(enabled = enabled, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        XhsCheckCircle(checked = checked)
        Spacer(modifier = Modifier.width(Dimens.s4))
        Text(text = label, style = XhsType.meta, color = XhsColor.Text2)
    }
}

/**
 * A3「没有账号？注册」/ A4「已有账号？前往登录」（线框原文，动作词带下划线加粗）。
 *
 * 不用 `XhsTextAction`：那是顶栏动作档（17sp），这里要与提示文字同档（线框两者同字号）。
 * 整行高 44 = 触控热区下限。
 */
@Composable
internal fun AuthFooterLink(
    prefix: String,
    action: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.minTouchTarget)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = prefix, style = XhsType.body, color = XhsColor.Text2)
        Text(
            text = action,
            style = XhsType.s(14, emphasis = true),
            color = if (enabled) XhsColor.Text1 else XhsColor.Text3,
            textDecoration = TextDecoration.Underline,
        )
    }
}
