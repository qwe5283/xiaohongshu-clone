package com.xiaohongshu.app.feature.auth

/**
 * WS-Auth 入口：A3 登录页（含 A5-1 失败态 / A5-2 提交中 / A6 成功）与 A4 注册页。
 *
 * 只有 [LoginRoute] / [RegisterRoute] 是包外可见入口（签名固定，`AppNavHost` 直接调用）；
 * 控件在同目录 [AuthComponents]、状态与校验在 [AuthViewModel]、A4→A3 回填在 [AuthPrefill]。
 *
 * A1（游客态差异）与 A2（拦截本身）分别由 WS-Home / MainScaffold 拥有，本工作流只保证
 * 「从 A2 可达的登录页行为正确」：← 回来源页、成功走 `popLogin()`。
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaohongshu.app.R
import com.xiaohongshu.app.core.design.Dimens
import com.xiaohongshu.app.core.design.XhsColor
import com.xiaohongshu.app.core.ui.XhsFormErrorBar
import com.xiaohongshu.app.core.ui.XhsIconButton
import com.xiaohongshu.app.core.ui.XhsPrimaryButton
import com.xiaohongshu.app.core.ui.XhsTextAction
import com.xiaohongshu.app.core.ui.XhsTopBar
import com.xiaohongshu.app.di.LocalAppContainer
import com.xiaohongshu.app.di.appViewModel
import com.xiaohongshu.app.navigation.AppNavigator

// ==================================================================== A3 登录页

/**
 * A3 登录页（推入式全屏）：← 返回来源页、右上「帮助」、LOGO + 标语、用户名/密码、全圆角主按钮、
 * 协议勾选、底部「没有账号？注册」。账号密码登录，**无第三方/一键登录**（线框明确排除）。
 */
@Composable
fun LoginRoute(navigator: AppNavigator) {
    val container = LocalAppContainer.current
    val vm: LoginViewModel = appViewModel { LoginViewModel(it.sessionManager, it.toastController) }
    val state by vm.state.collectAsStateWithLifecycle()

    // A4 → A3：注册成功后回填用户名（不自动登录）。取走即清空，不会残留到下次进入。
    LaunchedEffect(Unit) { AuthPrefill.consumeUsername()?.let(vm::prefillUsername) }

    /** 离开登录页 = 放弃登录：丢弃暂存的游客动作（`LoginGate.clear` 的语义），否则用户改从
     *  悬浮条登录时会把很久以前被拦截的动作补跑一遍。 */
    val leave: () -> Unit = {
        if (!state.submitting) {
            container.loginGate.clear()
            navigator.back()
        }
    }

    // A5-2：提交中「此状态 ← 不可返回」；其余情况系统返回键与顶栏 ← 走同一条路径
    BackHandler { leave() }

    LoginScreen(
        state = state,
        onUsernameChange = vm::updateUsername,
        onPasswordChange = vm::updatePassword,
        onToggleAgreement = vm::toggleAgreement,
        onBack = leave,
        // 「帮助」是 #9 占位：只弹全局 Toast，不新建页面
        onHelp = { container.toastController.show(HelpPlaceholderToast) },
        // A6：VM 先弹 Toast「登录成功」，再 popLogin() 弹回来源页并补跑被拦截的动作。
        // **不自己 popBackStack**（§4.4 / A6）：popLogin() 内部是「consumePending() + popBackStack()」。
        onLogin = { vm.submit(onSuccess = navigator::popLogin) },
        onRegister = { if (!state.submitting) navigator.toRegister() },
    )
}

/** A3 无状态内容层（方便 Preview，也强制状态提升到 VM）。 */
@Composable
private fun LoginScreen(
    state: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onToggleAgreement: () -> Unit,
    onBack: () -> Unit,
    onHelp: () -> Unit,
    onLogin: () -> Unit,
    onRegister: () -> Unit,
) {
    AuthScaffold(
        // 线框 A3 顶栏：左 ← 返回，右「帮助」；无居中标题
        topBar = {
            XhsTopBar(
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
                    // A5-2 线框里「帮助」同样置灰
                    XhsTextAction(
                        text = HelpLabel,
                        onClick = onHelp,
                        enabled = !state.submitting,
                    )
                },
            )
        },
        showLogo = true,
    ) {
        // A5-1：错误条在表单**上方**；已填内容由 VM 状态保留，可直接重试
        AuthErrorBar(message = state.error)

        AuthTextField(
            value = state.username,
            onValueChange = onUsernameChange,
            placeholder = UsernamePlaceholder,
            enabled = !state.submitting,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next,
        )
        Spacer(modifier = Modifier.height(Dimens.s12))
        AuthTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            placeholder = PasswordPlaceholder,
            enabled = !state.submitting,
            password = true, // PasswordVisualTransformation：掩码显示
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        )

        Spacer(modifier = Modifier.height(Dimens.s24))
        // A5-2：按钮禁用 + 「登录中...」；未勾选协议时禁用（见 LoginViewModel 顶部取舍说明）
        XhsPrimaryButton(
            text = LoginLabel,
            onClick = onLogin,
            enabled = state.canSubmit,
            loading = state.submitting,
            loadingText = LoginSubmitLabel,
        )

        Spacer(modifier = Modifier.height(Dimens.s8))
        AuthAgreementRow(
            checked = state.agreed,
            onToggle = onToggleAgreement,
            enabled = !state.submitting,
        )

        Spacer(modifier = Modifier.height(Dimens.s16))
        AuthFooterLink(
            prefix = "没有账号？",
            action = "注册",
            onClick = onRegister,
            enabled = !state.submitting,
        )
        Spacer(modifier = Modifier.height(Dimens.s32))
    }
}

// ==================================================================== A4 注册页

/**
 * A4 注册页：标题「注册小红书」+ ← 、四个输入（用户名 3-20 必填 / 密码 6-20 必填 /
 * 昵称选填 ≤20 / 手机号选填 `1[3-9]\d{9}`）、「注册」主按钮、同款协议勾选、底部「已有账号？前往登录」。
 */
@Composable
fun RegisterRoute(navigator: AppNavigator) {
    val vm: RegisterViewModel = appViewModel { RegisterViewModel(it.sessionManager, it.toastController) }
    val state by vm.state.collectAsStateWithLifecycle()

    // A5-2：提交中「此状态 ← 不可返回」；其余情况系统返回键与顶栏 ← 一致（回 A3）
    BackHandler { if (!state.submitting) navigator.back() }

    RegisterScreen(
        state = state,
        onUsernameChange = vm::updateUsername,
        onPasswordChange = vm::updatePassword,
        onNicknameChange = vm::updateNickname,
        onPhoneChange = vm::updatePhone,
        onToggleAgreement = vm::toggleAgreement,
        // 不自动登录：把用户名交给 A3 回填，登录动作留给用户在登录页完成（线框 A4）
        onSubmit = {
            vm.submit(onSuccess = { username ->
                AuthPrefill.setUsername(username)
                navigator.back()
            })
        },
        // 返回来源页（A3）：本页是从 A3 推入的，back() 即回登录页
        onBack = { if (!state.submitting) navigator.back() },
    )
}

/** A4 无状态内容层。 */
@Composable
private fun RegisterScreen(
    state: RegisterUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onNicknameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onToggleAgreement: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    AuthScaffold(
        // 线框 A4 顶栏：← + 居中「注册小红书」
        topBar = {
            XhsTopBar(
                title = "注册小红书",
                navigationIcon = {
                    XhsIconButton(
                        iconRes = R.drawable.ic_chevron_left,
                        onClick = onBack,
                        tint = if (state.submitting) XhsColor.Text3 else XhsColor.Text1,
                        contentDescription = "返回",
                    )
                },
            )
        },
    ) {
        // A4/A5-1：与登录页同款错误条（客户端校验失败 或 服务端失败，如 1003 用户名已存在）
        AuthErrorBar(message = state.error)

        AuthTextField(
            value = state.username,
            onValueChange = onUsernameChange,
            placeholder = "设置用户名（3-20字符）*",
            enabled = !state.submitting,
            imeAction = ImeAction.Next,
        )
        Spacer(modifier = Modifier.height(Dimens.s12))
        AuthTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            placeholder = "设置密码（6-20字符）*",
            enabled = !state.submitting,
            password = true,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Next,
        )
        Spacer(modifier = Modifier.height(Dimens.s12))
        AuthTextField(
            value = state.nickname,
            onValueChange = onNicknameChange,
            placeholder = "昵称（选填）",
            enabled = !state.submitting,
            imeAction = ImeAction.Next,
        )
        Spacer(modifier = Modifier.height(Dimens.s12))
        AuthTextField(
            value = state.phone,
            onValueChange = onPhoneChange,
            placeholder = "手机号（选填）",
            enabled = !state.submitting,
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Done,
        )

        Spacer(modifier = Modifier.height(Dimens.s24))
        XhsPrimaryButton(
            text = RegisterLabel,
            onClick = onSubmit,
            enabled = state.canSubmit,
            loading = state.submitting,
            loadingText = RegisterSubmitLabel,
        )

        Spacer(modifier = Modifier.height(Dimens.s8))
        AuthAgreementRow(
            checked = state.agreed,
            onToggle = onToggleAgreement,
            enabled = !state.submitting,
        )

        Spacer(modifier = Modifier.height(Dimens.s16))
        AuthFooterLink(
            prefix = "已有账号？",
            action = "前往登录",
            onClick = onBack,
            enabled = !state.submitting,
        )
        Spacer(modifier = Modifier.height(Dimens.s32))
    }
}

// ==================================================================== 共用骨架

/**
 * A3/A4 共用骨架：白底 + 顶栏 + 可滚动表单区。
 *
 * - 表单区左右边距 = [Dimens.pagePadding]（§4.1 页边距 16）；
 * - `verticalScroll` + `imePadding`：键盘弹起时表单可滚，「没有账号？注册」不会被顶出屏幕
 *   （Manifest 为 `adjustResize` + edge-to-edge，需要主动吃 IME inset）。
 */
@Composable
private fun AuthScaffold(
    topBar: @Composable () -> Unit,
    showLogo: Boolean = false,
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
                // A3 有居中 LOGO + 标语；A4 线框没有 LOGO，直接进表单
                if (showLogo) {
                    AuthLogoHeader()
                    Spacer(modifier = Modifier.height(Dimens.s24))
                }

                Column(
                    modifier = Modifier.padding(horizontal = Dimens.pagePadding),
                    content = content,
                )
            }
        }
    }
}

/** A5-1 错误条：非空时展示在表单**上方**（`XhsFormErrorBar` 已处理空串）。 */
@Composable
private fun AuthErrorBar(message: String) {
    if (message.isBlank()) return
    XhsFormErrorBar(message = message)
    Spacer(modifier = Modifier.height(Dimens.s8))
}
