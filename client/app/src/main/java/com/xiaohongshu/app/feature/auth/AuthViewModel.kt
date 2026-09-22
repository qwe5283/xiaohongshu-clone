package com.xiaohongshu.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohongshu.app.core.net.ApiResult
import com.xiaohongshu.app.core.net.userMessage
import com.xiaohongshu.app.core.ui.ToastController
import com.xiaohongshu.app.data.local.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Auth 页当前形态：登录 / 注册。 */
internal enum class AuthMode { Login, Register }

/**
 * A3 登录 / A4 注册合并后的 Auth 页状态。
 *
 * 用户名、密码、昵称、手机号、协议勾选只存在这一份状态里；登录/注册切换只改 [mode]，
 * **不发生导航**，所以已输入内容（尤其用户名/密码）原地保留，不依赖回填或 SavedState。
 *
 * 错误不再进 state：客户端校验失败与服务端业务失败统一走全局 Toast（见 [AuthViewModel]）。
 */
internal data class AuthUiState(
    val mode: AuthMode = AuthMode.Login,
    val username: String = "",
    val password: String = "",
    val nickname: String = "",
    val phone: String = "",
    /** 协议勾选（登录/注册必填，见 [AuthViewModel] 顶部的取舍说明）。 */
    val agreed: Boolean = false,
    /** A5-2 提交中：按钮禁用 +「登录中.../注册中...」+ ← 不可返回。 */
    val submitting: Boolean = false,
) {
    /** 勾选协议后才允许提交（登录/注册同一口径）。 */
    val canSubmit: Boolean get() = agreed && !submitting

    /** 当前是否注册态（页面按它切换顶栏/字段/按钮文案）。 */
    val isRegister: Boolean get() = mode == AuthMode.Register
}

// ---- A4 字段约束（契约 §1.1：username 3–20 必填 / password 6–20 必填 / nickname ≤20 / phone `1[3-9]\d{9}`）----

internal const val UsernameMinLength = 3
internal const val UsernameMaxLength = 20
internal const val PasswordMinLength = 6
internal const val PasswordMaxLength = 20
internal const val NicknameMaxLength = 20

/** 手机号格式 `1[3-9]\d{9}`（契约 §1.1）。 */
private val PhonePattern = Regex("1[3-9]\\d{9}")

/**
 * A4 注册客户端校验（仅注册态提交前跑）。
 *
 * 返回非空 = 不通过（文案直接交给全局 Toast）。文案来源：
 * - 「用户名长度为3-20个字符」= A5-1 失败示例的定稿原文；
 * - 「密码长度为6-20个字符」/「昵称最多20个字符」= 同句式（后者对齐 F3-1 的「名字最多20个字符」）；
 * - 「手机号格式不正确」= F3-1 定稿文案（同一约束 `1[3-9]\d{9}`）；
 * - 「请输入用户名」/「请输入密码」= A3/A4 占位的定稿原文（未填写时不另造文案）。
 */
internal fun validateRegister(state: AuthUiState): String? = when {
    state.username.isBlank() -> UsernamePlaceholder
    state.username.length !in UsernameMinLength..UsernameMaxLength -> "用户名长度为3-20个字符"
    state.password.isBlank() -> PasswordPlaceholder
    state.password.length !in PasswordMinLength..PasswordMaxLength -> "密码长度为6-20个字符"
    state.nickname.length > NicknameMaxLength -> "昵称最多20个字符"
    state.phone.isNotBlank() && !PhonePattern.matches(state.phone) -> "手机号格式不正确"
    else -> null
}

/**
 * A3 登录 / A4 注册合并页 ViewModel。
 *
 * **协议勾选 vs 提交按钮**（沿用原登录页取舍）：未勾选时按钮禁用（45% 透明），[submit] 内
 * 仍保留一次 `agreed` 防御判断（正常路径到不了）。
 *
 * 反馈与错误：客户端校验失败、服务端业务失败（1001/1002/1003/1004…）与网络层失败统一走
 * 全局 Toast（I2），页面不再有错误条；失败不清空任何已输入内容，可直接重试。
 *
 * 成功路径：
 * - 登录成功 → Toast「登录成功」+ [submit] 的 `onSuccess`（Route 调 `popLogin()` 弹回来源页
 *   并补跑被拦截的游客动作，见 §4.4 / A6）；
 * - 注册成功**不自动登录** → Toast「注册成功，请登录」+ 切回登录态，**仅保留用户名**
 *   （密码/昵称/手机号清空，协议勾选复位），登录动作留给用户完成。
 */
internal class AuthViewModel(
    private val session: SessionManager,
    private val toasts: ToastController,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun updateUsername(value: String) = edit { it.copy(username = value.trim()) }

    /** 密码不做 trim（空格可能是密码的一部分，`SessionManager.login/register` 也不 trim 密码）。 */
    fun updatePassword(value: String) = edit { it.copy(password = value) }

    fun updateNickname(value: String) = edit { it.copy(nickname = value.trim()) }

    fun updatePhone(value: String) = edit { it.copy(phone = value.trim()) }

    fun toggleAgreement() = edit { it.copy(agreed = !it.agreed) }

    /** 登录 ⇄ 注册：只切 [AuthUiState.mode]，保留所有已输入内容（含密码）；提交中忽略。 */
    fun switchMode(mode: AuthMode) {
        if (_state.value.submitting) return
        _state.value = _state.value.copy(mode = mode)
    }

    /**
     * 提交当前模式表单。
     *
     * @param onSuccess 仅登录成功时回调（导航是一次性事件，交给 Route 触发）。
     */
    fun submit(onSuccess: () -> Unit) {
        val current = _state.value
        if (current.submitting) return // A5-2：防重复提交
        if (!current.agreed) return // 防御：按钮已禁用，正常路径到不了

        when (current.mode) {
            AuthMode.Login -> submitLogin(current, onSuccess)
            AuthMode.Register -> submitRegister(current)
        }
    }

    private fun submitLogin(current: AuthUiState, onSuccess: () -> Unit) {
        if (current.username.isBlank()) {
            toasts.show(UsernamePlaceholder)
            return
        }
        if (current.password.isBlank()) {
            toasts.show(PasswordPlaceholder)
            return
        }

        _state.value = current.copy(submitting = true)
        viewModelScope.launch {
            when (val result = session.login(current.username, current.password)) {
                // A6：Toast「登录成功」→ Route 调 popLogin() 弹回来源页并补跑被拦截的动作
                is ApiResult.Ok -> {
                    _state.value = _state.value.copy(submitting = false)
                    toasts.show(LoginSuccessToast)
                    onSuccess()
                }

                // 1001/1002/1004…：服务端 message 原样 Toast，已填内容保留可重试
                is ApiResult.Biz -> {
                    _state.value = _state.value.copy(submitting = false)
                    toasts.show(result.userMessage())
                }

                // 网络层失败 → 全局 Toast，停留本页可重试
                else -> {
                    _state.value = _state.value.copy(submitting = false)
                    toasts.show(result.userMessage())
                }
            }
        }
    }

    private fun submitRegister(current: AuthUiState) {
        val invalid = validateRegister(current)
        if (invalid != null) {
            // A5-1：客户端校验失败 → 全局 Toast，已填内容保留可重试
            toasts.show(invalid)
            return
        }

        _state.value = current.copy(submitting = true)
        viewModelScope.launch {
            val result = session.register(
                username = current.username,
                password = current.password,
                nickname = current.nickname.ifEmpty { null },
                phone = current.phone.ifEmpty { null },
            )
            when (result) {
                is ApiResult.Ok -> {
                    // 注册成功不自动登录：Toast「注册成功，请登录」→ 切回登录态，仅保留用户名。
                    // 协议勾选一并复位，登录前重新确认（与合并前「回到登录页」的口径一致）。
                    toasts.show(RegisterSuccessToast)
                    _state.value = AuthUiState(
                        mode = AuthMode.Login,
                        username = current.username,
                    )
                }

                // 1003「该用户名已被注册」/ 5001「参数错误」…：服务端 message 原样 Toast
                is ApiResult.Biz -> {
                    _state.value = _state.value.copy(submitting = false)
                    toasts.show(result.userMessage())
                }

                else -> {
                    _state.value = _state.value.copy(submitting = false)
                    toasts.show(result.userMessage())
                }
            }
        }
    }

    /** 字段编辑只改内容（错误已不在 state 里，无需清错误）。 */
    private fun edit(transform: (AuthUiState) -> AuthUiState) {
        _state.value = transform(_state.value)
    }
}
