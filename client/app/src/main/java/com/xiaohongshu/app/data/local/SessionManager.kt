package com.xiaohongshu.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.xiaohongshu.app.core.net.ApiClient
import com.xiaohongshu.app.core.net.ApiResult
import com.xiaohongshu.app.core.net.TokenHolder
import com.xiaohongshu.app.core.net.apiCall
import com.xiaohongshu.app.core.net.map
import com.xiaohongshu.app.core.net.unwrap
import com.xiaohongshu.app.data.dto.LoginRequest
import com.xiaohongshu.app.data.dto.RegisterRequest
import com.xiaohongshu.app.data.dto.UpdateUserRequest
import com.xiaohongshu.app.data.dto.UserDto
import com.xiaohongshu.app.data.api.UserApi
import com.xiaohongshu.app.data.mapper.toDomain
import com.xiaohongshu.app.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore("xhs_session")

/**
 * 登录态本地持久化（token + 用户快照）。
 *
 * 持久化的目的是 I1：重启 APP 时本地有 token 则**静默校验**恢复登录态，
 * 因此用户快照也一并缓存，首帧不必等网络即可渲染「已登录」骨架。
 */
class SessionLocalStore(private val context: Context) {

    private val keyToken = stringPreferencesKey("token")
    private val keyUser = stringPreferencesKey("user_json")

    data class Snapshot(val token: String, val userJson: String)

    suspend fun read(): Snapshot? {
        val prefs = context.sessionDataStore.data.first()
        val token = prefs[keyToken].orEmpty()
        if (token.isBlank()) return null
        return Snapshot(token, prefs[keyUser].orEmpty())
    }

    suspend fun write(token: String, user: UserDto) {
        val json = Json.encodeToString(UserDto.serializer(), user)
        context.sessionDataStore.edit { prefs ->
            prefs[keyToken] = token
            prefs[keyUser] = json
        }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }
}

/** 登录态。 */
data class SessionState(
    /** 本地是否已有 token（乐观值；[validated] 前不代表服务端认可）。 */
    val loggedIn: Boolean = false,
    /** 当前用户；未登录或尚未加载到资料时为 [User.Empty]。 */
    val user: User = User.Empty,
    /** 是否已完成一次静默校验（I1），用于首帧决定是否展示骨架。 */
    val validated: Boolean = false,
)

/**
 * 全局登录态中心。
 *
 * 职责：
 * - 把 token 写入 [TokenHolder]，供 OkHttp 拦截器注入 `Authorization`；
 * - 承载注册/登录/登出/资料更新；
 * - 启动时静默校验（I1），失败即清态；
 * - 暴露 [state] 驱动全局 UI 变化（A6：游客悬浮条消失、＋变主色、消息角标、☰ 变点点气泡）。
 */
class SessionManager(
    private val context: Context,
    private val userApi: UserApi,
    private val tokenHolder: TokenHolder,
) {

    private val store = SessionLocalStore(context)

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** 当前 token（内存态，避免每次请求读 DataStore）。 */
    private val currentToken: String? get() = tokenHolder.token()

    val isLoggedIn: Boolean get() = _state.value.loggedIn
    val currentUserId: Long get() = _state.value.user.id

    // ---------------------------------------------------------------- 启动恢复

    /**
     * I1：启动时本地有 token → 调 `GET /api/user/me` 静默校验。
     * 成功则刷新用户资料；失败（含 401）则清态回到游客。
     */
    suspend fun restore() {
        val snapshot = store.read()
        if (snapshot == null) {
            _state.value = SessionState(loggedIn = false, validated = true)
            return
        }
        tokenHolder.set(snapshot.token)
        _state.value = SessionState(loggedIn = true, user = decodeUser(snapshot.userJson), validated = false)

        when (val result = apiCall { userApi.me().unwrap() }) {
            is ApiResult.Ok -> {
                val user = result.data.toDomain()
                _state.value = SessionState(loggedIn = true, user = user, validated = true)
                store.write(snapshot.token, result.data)
            }
            else -> {
                // 含 Unauthorized 与网络失败：静默失败不弹窗，回到游客态
                clearLocal()
            }
        }
    }

    // ---------------------------------------------------------------- 注册 / 登录

    /** A4：注册成功**不自动登录**，调用方负责回登录页并预填用户名。 */
    suspend fun register(
        username: String,
        password: String,
        nickname: String?,
        phone: String?,
    ): ApiResult<User> = apiCall {
        userApi.register(
            RegisterRequest(
                username = username.trim(),
                password = password,
                nickname = nickname?.trim()?.takeIf { it.isNotEmpty() },
                phone = phone?.trim()?.takeIf { it.isNotEmpty() },
            ),
        ).unwrap()
    }.map { it.toDomain() }

    /** A3：登录成功 → 持久化并切换全局态（A6 全局变化由此驱动）。 */
    suspend fun login(username: String, password: String): ApiResult<User> = apiCall {
        userApi.login(LoginRequest(username.trim(), password)).unwrap()
    }.map { dto ->
        tokenHolder.set(dto.token)
        store.write(dto.token, dto.user)
        val user = dto.user.toDomain()
        _state.value = SessionState(loggedIn = true, user = user, validated = true)
        user
    }

    /** 拉取最新资料（F1 下拉/返回时刷新统计数字）。 */
    suspend fun refreshMe(): ApiResult<User> = apiCall { userApi.me().unwrap() }.map { dto ->
        val user = dto.toDomain()
        currentToken?.let { store.write(it, dto) }
        _state.value = _state.value.copy(user = user)
        user
    }

    /** F3：仅提交有变化的字段（调用方负责 diff）。 */
    suspend fun updateProfile(body: UpdateUserRequest): ApiResult<User> = apiCall {
        userApi.update(body).unwrap()
    }.map { dto ->
        val user = dto.toDomain()
        currentToken?.let { store.write(it, dto) }
        _state.value = _state.value.copy(user = user)
        user
    }

    /** F6：退出登录 → 回游客首页（A1，带登录悬浮条）。 */
    suspend fun logout() {
        clearLocal()
    }

    /** I1：Token 失效。清登录态（不导航，导航由全局事件消费方负责）。 */
    suspend fun onSessionExpired() {
        clearLocal()
    }

    private suspend fun clearLocal() {
        tokenHolder.set(null)
        store.clear()
        _state.value = SessionState(loggedIn = false, user = User.Empty, validated = true)
    }

    private fun decodeUser(json: String): User = runCatching {
        ApiClient.json.decodeFromString(UserDto.serializer(), json).toDomain()
    }.getOrDefault(User.Empty)
}
