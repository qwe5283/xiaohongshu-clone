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

/**
 * A3 登录页状态。
 *
 * A5-1「已填内容保留」是**结构上**保证的：用户名/密码只存在这份状态里，失败只写 [error]，
 * 不清空字段，因此重试时用户不用重新输入。
 */
internal data class LoginUiState(
    val username: String = "",
    val password: String = "",
    /** 协议勾选（A3 必填，见 [LoginViewModel] 顶部的取舍说明）。 */
    val agreed: Boolean = false,
    /** A5-1 错误条文案（空 = 不显示）。服务端 `message` 原样展示。 */
    val error: String = "",
    /** A5-2 提交中：按钮禁用 +「登录中...」+ ← 不可返回。 */
    val submitting: Boolean = false,
) {
    /** 勾选协议后才允许提交（未勾选时按钮禁用）。 */
    val canSubmit: Boolean get() = agreed && !submitting
}

/**
 * A3 登录页（含 A5-1 失败态 / A5-2 提交中 / A6 成功）。
 *
 * **协议勾选 vs 登录按钮**（规范 §7 A3 留的取舍项，本实现的选择）：
 * 未勾选时**按钮禁用**（`XhsPrimaryButton(enabled = false)` → 45% 透明，视觉与 A5-2 的 `dis` 同源）。
 * 理由：① 真实 App 是硬门槛，登录接口不该在未同意协议时发出；
 * ② 禁用态在本设计系统里已有定义，**不需要新增任何文案**（若改为「可点但报错」，就必须自创一条
 *    A5-1 错误条文案，而 §4.7 要求文案定稿后不自创）；
 * ③ 线框 A5-2 的禁用态是显式加 `dis` 类并置灰的，而 A3 的按钮沿用 `.btn.rd.blk`——低保真线框里
 *    所有按钮都是同一个黑胶囊类，A3「未勾选 + 按钮可见」不足以作为「可提交」的证据。
 * 另外 [submit] 里仍保留一次 `agreed` 防御判断（正常路径到不了）。
 *
 * 其余行为：客户端先挡空值（不发明知无效的请求）；业务失败（1001 用户不存在 / 1002 密码错误 /
 * 1004 被禁用）把服务端 `message` **原样**写进错误条；网络层失败走全局 Toast（线框 A5-1 附注
 * 「网络层错误走全局 Toast（I2）」）。成功：Toast「登录成功」+ 回调（Route 调 `popLogin()`，
 * 由它补跑被拦截的游客动作并弹回来源页，见 §4.4 / A6）。
 */
internal class LoginViewModel(
    private val session: SessionManager,
    private val toasts: ToastController,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun updateUsername(value: String) = edit { it.copy(username = value.trim()) }

    /** 密码不做 trim（空格可能是密码的一部分，`SessionManager.login` 也不 trim 密码）。 */
    fun updatePassword(value: String) = edit { it.copy(password = value) }

    fun toggleAgreement() = edit { it.copy(agreed = !it.agreed) }

    /** A4 → A3：注册成功后回填用户名（此时密码仍为空，符合「请登录」的引导）。 */
    fun prefillUsername(username: String) = edit { it.copy(username = username) }

    /** 编辑任一字段即清掉上一条错误条，避免「已修正却还挂着旧报错」。 */
    private fun edit(transform: (LoginUiState) -> LoginUiState) {
        _state.value = transform(_state.value).copy(error = "")
    }

    /**
     * 提交登录。成功回调交给 Route（一次性事件不用 SharedFlow，规范 §3）。
     */
    fun submit(onSuccess: () -> Unit) {
        val current = _state.value
        if (current.submitting) return // A5-2：防重复提交
        if (!current.agreed) return // 防御：按钮已禁用，正常路径到不了
        if (current.username.isBlank()) {
            _state.value = current.copy(error = UsernamePlaceholder)
            return
        }
        if (current.password.isBlank()) {
            _state.value = current.copy(error = PasswordPlaceholder)
            return
        }

        _state.value = current.copy(submitting = true, error = "")
        viewModelScope.launch {
            when (val result = session.login(current.username, current.password)) {
                // A6：Toast「登录成功」→ Route 调 popLogin() 弹回来源页并补跑被拦截的动作
                is ApiResult.Ok -> {
                    _state.value = _state.value.copy(submitting = false)
                    toasts.show(LoginSuccessToast)
                    onSuccess()
                }

                // A5-1：业务失败（1001/1002/1004…）→ 服务端 message 原样进错误条，已填内容保留
                is ApiResult.Biz -> {
                    _state.value = _state.value.copy(submitting = false, error = result.userMessage())
                }

                // 网络层失败 → 全局 Toast，停留本页可重试（线框 A5-1 附注）
                else -> {
                    _state.value = _state.value.copy(submitting = false)
                    toasts.show(result.userMessage())
                }
            }
        }
    }
}

/** A4 注册页状态。 */
internal data class RegisterUiState(
    val username: String = "",
    val password: String = "",
    val nickname: String = "",
    val phone: String = "",
    val agreed: Boolean = false,
    val error: String = "",
    val submitting: Boolean = false,
) {
    /** 勾选协议后才允许提交（与 A3 同一口径）。 */
    val canSubmit: Boolean get() = agreed && !submitting
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
 * A4 客户端校验（提交前跑）。
 *
 * 返回非空 = 不通过（文案直接进 A5-1 错误条）。文案来源：
 * - 「用户名长度为3-20个字符」= 线框 A5-1 里给出的失败示例原文；
 * - 「密码长度为6-20个字符」/「昵称最多20个字符」= 同句式（后者对齐 F3-1 的「名字最多20个字符」）；
 * - 「手机号格式不正确」= F3-1 定稿文案（同一约束 `1[3-9]\d{9}`）；
 * - 「请输入用户名」/「请输入密码」= 线框 A3/A4 的占位原文（未填写时不另造文案）。
 */
internal fun validateRegister(state: RegisterUiState): String? = when {
    state.username.isBlank() -> UsernamePlaceholder
    state.username.length !in UsernameMinLength..UsernameMaxLength -> "用户名长度为3-20个字符"
    state.password.isBlank() -> PasswordPlaceholder
    state.password.length !in PasswordMinLength..PasswordMaxLength -> "密码长度为6-20个字符"
    state.nickname.length > NicknameMaxLength -> "昵称最多20个字符"
    state.phone.isNotBlank() && !PhonePattern.matches(state.phone) -> "手机号格式不正确"
    else -> null
}

/**
 * A4 注册页（含 A5-1 同款错误条 / A5-2 提交中）。
 *
 * 与 A3 的差异：
 * - 提交前先跑 [validateRegister]（长度/手机号格式/必填），不通过就停在 A5-1 形态；
 * - 服务端失败（如 `1003` 用户名已存在）同样把 `message` 原样写进错误条；
 * - **成功不自动登录**（`SessionManager.register` 的 KDoc 明确如此）：Toast「注册成功，请登录」，
 *   把用户名交回 Route 写入 [AuthPrefill] 后返回 A3 预填，登录动作留给用户在 A3 完成。
 */
internal class RegisterViewModel(
    private val session: SessionManager,
    private val toasts: ToastController,
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun updateUsername(value: String) = edit { it.copy(username = value.trim()) }

    fun updatePassword(value: String) = edit { it.copy(password = value) }

    fun updateNickname(value: String) = edit { it.copy(nickname = value.trim()) }

    fun updatePhone(value: String) = edit { it.copy(phone = value.trim()) }

    fun toggleAgreement() = edit { it.copy(agreed = !it.agreed) }

    private fun edit(transform: (RegisterUiState) -> RegisterUiState) {
        _state.value = transform(_state.value).copy(error = "")
    }

    /**
     * 提交注册。[onSuccess] 收到的是**提交用的用户名**（Route 用它做 A3 回填）。
     */
    fun submit(onSuccess: (String) -> Unit) {
        val current = _state.value
        if (current.submitting) return // A5-2：防重复提交
        if (!current.agreed) return // 防御：按钮已禁用，正常路径到不了

        val invalid = validateRegister(current)
        if (invalid != null) {
            // A4/A5-1：客户端校验失败 → 同款错误条，已填内容保留
            _state.value = current.copy(error = invalid)
            return
        }

        _state.value = current.copy(submitting = true, error = "")
        viewModelScope.launch {
            val result = session.register(
                username = current.username,
                password = current.password,
                nickname = current.nickname.ifEmpty { null },
                phone = current.phone.ifEmpty { null },
            )
            when (result) {
                is ApiResult.Ok -> {
                    _state.value = _state.value.copy(submitting = false)
                    toasts.show(RegisterSuccessToast)
                    onSuccess(current.username)
                }

                // 1003「该用户名已被注册」/ 5001「参数错误」…：服务端 message 原样展示
                is ApiResult.Biz -> {
                    _state.value = _state.value.copy(submitting = false, error = result.userMessage())
                }

                else -> {
                    _state.value = _state.value.copy(submitting = false)
                    toasts.show(result.userMessage())
                }
            }
        }
    }
}
