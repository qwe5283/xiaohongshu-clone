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
import com.xiaohongshu.app.domain.model.LocalUserIdProvider
import com.xiaohongshu.app.domain.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore("xhs_session")

/**
 * 登录态本地持久化（token + 用户快照）。
 *
 * 持久化的目的是 I1：重启 APP 时本地有 token 则**静默校验**恢复登录态，
 * 因此用户快照也一并缓存，首帧不必等网络即可渲染「已登录」骨架。
 * 抽成接口：会话层集中做身份校验与写入（P1#1），测试用内存实现替换。
 */
interface SessionLocalStore {

    data class Snapshot(val token: String, val userJson: String)

    suspend fun read(): Snapshot?

    suspend fun write(token: String, user: UserDto)

    suspend fun clear()
}

/** [SessionLocalStore] 的 DataStore 实现（token + 用户快照 JSON）。 */
class DataStoreSessionLocalStore(private val context: Context) : SessionLocalStore {

    private val keyToken = stringPreferencesKey("token")
    private val keyUser = stringPreferencesKey("user_json")

    override suspend fun read(): SessionLocalStore.Snapshot? {
        val prefs = context.sessionDataStore.data.first()
        val token = prefs[keyToken].orEmpty()
        if (token.isBlank()) return null
        return SessionLocalStore.Snapshot(token, prefs[keyUser].orEmpty())
    }

    override suspend fun write(token: String, user: UserDto) {
        val json = Json.encodeToString(UserDto.serializer(), user)
        context.sessionDataStore.edit { prefs ->
            prefs[keyToken] = token
            prefs[keyUser] = json
        }
    }

    override suspend fun clear() {
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
    private val store: SessionLocalStore,
    private val userApi: UserApi,
    private val tokenHolder: TokenHolder,
) {

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** 当前 token（内存态，避免每次请求读 DataStore）。 */
    private val currentToken: String? get() = tokenHolder.token()

    /**
     * 会话代际（CodeReview-2026-10-14 发现 1）：login/logout/失效等身份切换点递增。
     * 请求发出时捕获，写回前在 [sessionMutex] 内校验——身份已变则整段丢弃，
     * 防止 A 的迟到资料与 B 的 token 配对持久化、或覆盖 B 的内存态。
     */
    private var sessionGeneration = 0L

    /** 身份校验 + 写回（持久化、内存态、代际）的互斥锁；网络请求在锁外执行。 */
    private val sessionMutex = Mutex()

    val isLoggedIn: Boolean get() = _state.value.loggedIn
    val currentUserId: Long get() = _state.value.user.id

    init {
        // 登录态变化的单一汇聚点：同步给 domain 层的 LocalUserIdProvider
        // （私聊气泡左右判断、WS 消息去重都用它，避免 domain 反向依赖 data/local）
        CoroutineScope(Dispatchers.Default).launch {
            _state.collect { LocalUserIdProvider.currentUserId = it.user.id }
        }
    }

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
        val generation = sessionGeneration
        tokenHolder.set(snapshot.token)
        _state.value = SessionState(loggedIn = true, user = decodeUser(snapshot.userJson), validated = false)

        val result = apiCall { userApi.me().unwrap() }
        if (result !is ApiResult.Ok) {
            // 旧恢复请求失败时不能清掉已经建立的新会话。
            sessionMutex.withLock {
                if (generation == sessionGeneration) {
                    clearLocalLocked()
                }
            }
            return
        }
        sessionMutex.withLock {
            // 校验期间被登录取代（如启动恢复慢时用户已手动登录）则整体丢弃
            if (generation != sessionGeneration) return@withLock
            _state.value = SessionState(loggedIn = true, user = result.data.toDomain(), validated = true)
            store.write(snapshot.token, result.data)
        }
    }

    // ---------------------------------------------------------------- 注册 / 登录

    /** A4：注册成功**不自动登录**；调用方负责切回登录态并仅保留用户名。 */
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

    /** A3：登录成功 → 持久化并切换全局态（A6 全局变化由此驱动）。登录本身是代际切换点。 */
    suspend fun login(username: String, password: String): ApiResult<User> {
        val result = apiCall { userApi.login(LoginRequest(username.trim(), password)).unwrap() }
        val dto = when (result) {
            is ApiResult.Ok -> result.data
            is ApiResult.Biz -> return result
            is ApiResult.Server -> return result
            is ApiResult.Network -> return result
            ApiResult.Unauthorized -> return ApiResult.Unauthorized
        }
        sessionMutex.withLock {
            tokenHolder.set(dto.token)
            store.write(dto.token, dto.user)
            sessionGeneration++
            _state.value = SessionState(loggedIn = true, user = dto.user.toDomain(), validated = true)
        }
        return ApiResult.Ok(dto.user.toDomain())
    }

    /** 拉取最新资料（F1 下拉/返回时刷新统计数字）。 */
    suspend fun refreshMe(): ApiResult<User> = sessionWrite(
        request = { userApi.me().unwrap() },
        writeBack = { dto ->
            val user = dto.toDomain()
            currentToken?.let { store.write(it, dto) }
            _state.value = _state.value.copy(user = user)
            user
        },
    )

    /** F3：仅提交有变化的字段（调用方负责 diff）。 */
    suspend fun updateProfile(body: UpdateUserRequest): ApiResult<User> = sessionWrite(
        request = { userApi.update(body).unwrap() },
        writeBack = { dto ->
            val user = dto.toDomain()
            currentToken?.let { store.write(it, dto) }
            _state.value = _state.value.copy(user = user)
            user
        },
    )

    /** F6：退出登录 → 回游客首页（A1，带登录悬浮条）。 */
    suspend fun logout() {
        clearLocal()
    }

    /** I1：Token 失效。清登录态（不导航，导航由全局事件消费方负责）。 */
    suspend fun onSessionExpired() {
        clearLocal()
    }

    private suspend fun clearLocal() = sessionMutex.withLock {
        clearLocalLocked()
    }

    private suspend fun clearLocalLocked() {
        tokenHolder.set(null)
        store.clear()
        sessionGeneration++
        _state.value = SessionState(loggedIn = false, user = User.Empty, validated = true)
    }

    /**
     * 会话内请求的统一写回收口（CodeReview-2026-10-14 发现 1）：发出时捕获代际，
     * 响应回来后在 [sessionMutex] 内校验——身份已切换（退出/换号/失效）则整段丢弃
     * （不持久化、不改内存态），调用方收到 [ApiResult.Unauthorized]（会话不再认可该请求）。
     * 网络在锁外执行，锁只覆盖「校验 + 写回」，防止校验与写入之间被身份切换插入。
     */
    private suspend fun <D, R> sessionWrite(
        request: suspend () -> D,
        writeBack: suspend (D) -> R,
    ): ApiResult<R> {
        val generation = sessionGeneration
        return when (val result = apiCall { request() }) {
            is ApiResult.Ok -> sessionMutex.withLock<ApiResult<R>> {
                if (generation != sessionGeneration) ApiResult.Unauthorized
                else ApiResult.Ok(writeBack(result.data))
            }
            is ApiResult.Biz -> result
            is ApiResult.Server -> result
            is ApiResult.Network -> result
            ApiResult.Unauthorized -> ApiResult.Unauthorized
        }
    }

    private fun decodeUser(json: String): User = runCatching {
        ApiClient.json.decodeFromString(UserDto.serializer(), json).toDomain()
    }.getOrDefault(User.Empty)
}
