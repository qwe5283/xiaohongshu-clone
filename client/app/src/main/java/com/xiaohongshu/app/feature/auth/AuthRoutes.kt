package com.xiaohongshu.app.feature.auth

/**
 * WS-Auth 入口：A3 登录与 A4 注册合并为单个 Auth 页（含 A5-1 失败态 / A5-2 提交中 / A6 成功）。
 *
 * 只有 [AuthRoute] 是包外可见入口（`AppNavHost` 直接调用）。登录/注册切换只改
 * [AuthViewModel] 的 [AuthUiState.mode]，**不发生导航**，因此用户名、密码等已输入内容原地保留；
 * 注册成功后切回登录态并仅保留用户名（见 [AuthViewModel.submit]）。
 *
 * 错误反馈：客户端校验 / 服务端业务失败 / 网络失败统一走全局 Toast（I2），页面不再有错误条。
 *
 * A1（游客态差异）与 A2（拦截本身）分别由 WS-Home / MainScaffold 拥有，本工作流只保证
 * 「从 A2 可达的 Auth 页行为正确」：← 回来源页、成功走 `popLogin()`。
 */

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaohongshu.app.R
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.ui.XhsIconButton
import com.xiaohongshu.app.core.ui.XhsPrimaryButton
import com.xiaohongshu.app.core.ui.XhsTextAction
import com.xiaohongshu.app.core.ui.XhsTopBar
import com.xiaohongshu.app.di.LocalAppContainer
import com.xiaohongshu.app.di.appViewModel
import com.xiaohongshu.app.navigation.AppNavigator

// ============================================================ Auth 页（A3 登录 + A4 注册合并）

/**
 * Auth 页（推入式全屏）：登录态无标题 + 右上「帮助」+ LOGO/标语 + 用户名/密码 + 底部
 * 「没有账号？注册」；注册态居中标题「注册小红书」+ 4 个输入 + 底部「已有账号？前往登录」。
 * 两态共用同一份状态（[AuthUiState]），切换只改 mode、不导航，已输入内容全部保留。
 */
@Composable
fun AuthRoute(navigator: AppNavigator) {
    val container = LocalAppContainer.current
    val vm: AuthViewModel = appViewModel { AuthViewModel(it.sessionManager, it.toastController) }
    val state by vm.state.collectAsStateWithLifecycle()

    /** 离开 Auth 页 = 放弃登录：丢弃暂存的游客动作（`LoginGate.clear` 的语义），否则用户改从
     *  悬浮条登录时会把很久以前被拦截的动作补跑一遍。 */
    val leave: () -> Unit = {
        if (!state.submitting) {
            container.loginGate.clear()
            navigator.back()
        }
    }

    /**
     * 顶栏 ← 与系统返回键走同一条路径：A5-2 提交中不可返回；注册态先切回登录态
     * （等价原来的两级页面栈，且保留已输入内容），登录态才真正离开 Auth 页。
     */
    val back: () -> Unit = {
        if (!state.submitting) {
            if (state.isRegister) vm.switchMode(AuthMode.Login) else leave()
        }
    }
    BackHandler { back() }

    AuthScreen(
        state = state,
        onUsernameChange = vm::updateUsername,
        onPasswordChange = vm::updatePassword,
        onNicknameChange = vm::updateNickname,
        onPhoneChange = vm::updatePhone,
        onToggleAgreement = vm::toggleAgreement,
        onSwitchMode = vm::switchMode,
        onBack = back,
        // 「帮助」是占位：只弹全局 Toast，不新建页面（仅登录态顶栏显示）
        onHelp = { container.toastController.show(HelpPlaceholderToast) },
        // A6：VM 先弹 Toast「登录成功」，再 popLogin() 弹回来源页并补跑被拦截的动作。
        // **不自己 popBackStack**：popLogin() 内部是「consumePending() + popBackStack()」。
        onSubmit = { vm.submit(onSuccess = navigator::popLogin) },
    )
}

/** Auth 无状态内容层（登录/注册共用一个表单，按 [AuthUiState.mode] 切换文案与字段）。 */
@Composable
private fun AuthScreen(
    state: AuthUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onNicknameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onToggleAgreement: () -> Unit,
    onSwitchMode: (AuthMode) -> Unit,
    onBack: () -> Unit,
    onHelp: () -> Unit,
    onSubmit: () -> Unit,
) {
    AuthScaffold(
        // 登录态：左 ← + 右上「帮助」，无居中标题；注册态：左 ← + 居中「注册小红书」
        topBar = {
            XhsTopBar(
                title = if (state.isRegister) RegisterTitle else null,
                navigationIcon = {
                    XhsIconButton(
                        iconRes = R.drawable.ic_chevron_left,
                        // A5-2：提交中 ← 置灰且不可点
                        onClick = onBack,
                        tint = if (state.submitting) XhsColor.Text3 else XhsColor.Text1,
                        contentDescription = "返回",
                    )
                },
                actions = {
                    if (!state.isRegister) {
                        // A5-2：「帮助」同样置灰
                        XhsTextAction(
                            text = HelpLabel,
                            onClick = onHelp,
                            enabled = !state.submitting,
                            color = XhsColor.Text2,
                        )
                    }
                },
            )
        },
    ) {
        AuthTextField(
            value = state.username,
            onValueChange = onUsernameChange,
            placeholder = if (state.isRegister) RegisterUsernamePlaceholder else UsernamePlaceholder,
            enabled = !state.submitting,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next,
        )
        Spacer(modifier = Modifier.height(Dimens.s12))
        AuthTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            placeholder = if (state.isRegister) RegisterPasswordPlaceholder else PasswordPlaceholder,
            enabled = !state.submitting,
            password = true, // PasswordVisualTransformation：掩码显示
            keyboardType = KeyboardType.Password,
            // 登录态密码是最后一个输入 → Done；注册态后面还有昵称/手机号 → Next
            imeAction = if (state.isRegister) ImeAction.Next else ImeAction.Done,
        )

        if (state.isRegister) {
            Spacer(modifier = Modifier.height(Dimens.s12))
            AuthTextField(
                value = state.nickname,
                onValueChange = onNicknameChange,
                placeholder = NicknamePlaceholder,
                enabled = !state.submitting,
                imeAction = ImeAction.Next,
            )
            Spacer(modifier = Modifier.height(Dimens.s12))
            AuthTextField(
                value = state.phone,
                onValueChange = onPhoneChange,
                placeholder = PhonePlaceholder,
                enabled = !state.submitting,
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done,
            )
        }

        Spacer(modifier = Modifier.height(Dimens.s24))
        // A5-2：按钮禁用 +「登录中.../注册中...」；未勾选协议时禁用（与合并前同一口径）
        XhsPrimaryButton(
            text = if (state.isRegister) RegisterLabel else LoginLabel,
            onClick = onSubmit,
            enabled = state.canSubmit,
            loading = state.submitting,
            loadingText = if (state.isRegister) RegisterSubmitLabel else LoginSubmitLabel,
        )

        Spacer(modifier = Modifier.height(Dimens.s8))
        AuthAgreementRow(
            checked = state.agreed,
            onToggle = onToggleAgreement,
            enabled = !state.submitting,
        )

        Spacer(modifier = Modifier.height(Dimens.s16))
        AuthFooterLink(
            prefix = if (state.isRegister) RegisterSwitchPrefix else LoginSwitchPrefix,
            action = if (state.isRegister) RegisterSwitchAction else LoginSwitchAction,
            // 模式切换不导航：同一份 state 原地换 mode，已输入内容全部保留
            onClick = { onSwitchMode(if (state.isRegister) AuthMode.Login else AuthMode.Register) },
            enabled = !state.submitting,
        )
        Spacer(modifier = Modifier.height(Dimens.s32))
    }
}

// ==================================================================== 共用骨架

/**
 * Auth 共用骨架：白底 + 顶栏 + 居中 LOGO + 可滚动表单区（登录/注册两态 LOGO 常显）。
 *
 * - 表单区左右边距 = [Dimens.authPagePadding]（24，Auth 页专用；全项目页边距仍是
 *   [Dimens.pagePadding] 16）；
 * - `verticalScroll` + `imePadding`：键盘弹起时表单可滚，底部链接不会被顶出屏幕
 *   （Manifest 为 `adjustResize` + edge-to-edge，需要主动吃 IME inset）。
 */
@Composable
private fun AuthScaffold(
    topBar: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(XhsColor.Bg)
            .imePadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            topBar()

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(modifier = Modifier.height(Dimens.s24))
                AuthLogoHeader()
                Spacer(modifier = Modifier.height(Dimens.s24))

                Column(
                    modifier = Modifier.padding(horizontal = Dimens.authPagePadding),
                    content = content,
                )
            }
        }
    }
}
