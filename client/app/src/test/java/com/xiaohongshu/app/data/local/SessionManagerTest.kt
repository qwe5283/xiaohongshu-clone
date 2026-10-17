package com.xiaohongshu.app.data.local

import com.xiaohongshu.app.core.net.ApiEnvelope
import com.xiaohongshu.app.core.net.ApiResult
import com.xiaohongshu.app.core.net.TokenHolder
import com.xiaohongshu.app.core.net.ApiClient
import com.xiaohongshu.app.data.api.UserApi
import com.xiaohongshu.app.data.dto.LoginDto
import com.xiaohongshu.app.data.dto.LoginRequest
import com.xiaohongshu.app.data.dto.PageDto
import com.xiaohongshu.app.data.dto.RegisterRequest
import com.xiaohongshu.app.data.dto.UpdateUserRequest
import com.xiaohongshu.app.data.dto.UserBriefDto
import com.xiaohongshu.app.data.dto.UserDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 会话切换时请求写回的归属回归（CodeReview-设计与可维护性-2026-10-14 发现 1，P1）。
 *
 * 场景骨架：账号 A 的请求在途 → 退出并登录 B → A 的迟到响应返回。
 * 正确行为：迟到响应整体丢弃（不持久化、不改内存态），持久化里保持 (B token, B 资料)。
 */
class SessionManagerTest {

    /** 内存持久化：记录写入序列供断言。 */
    private class FakePersistence : SessionLocalStore {
        val writes = mutableListOf<Pair<String, UserDto>>()
        var snapshotToRestore: SessionLocalStore.Snapshot? = null
        override suspend fun read(): SessionLocalStore.Snapshot? = snapshotToRestore
        override suspend fun write(token: String, user: UserDto) {
            writes += token to user
        }

        override suspend fun clear() {
            snapshotToRestore = null
        }
    }

    /** me/update 登记门并挂起；login/register 按队列出队。 */
    private class FakeUserApi : UserApi {
        val meCalls = mutableListOf<CompletableDeferred<ApiEnvelope<UserDto>>>()
        val updateCalls = mutableListOf<CompletableDeferred<ApiEnvelope<UserDto>>>()
        val loginResults = ArrayDeque<ApiEnvelope<LoginDto>>()

        override suspend fun me(): ApiEnvelope<UserDto> =
            CompletableDeferred<ApiEnvelope<UserDto>>().also { meCalls += it }.await()

        override suspend fun login(body: LoginRequest): ApiEnvelope<LoginDto> = loginResults.removeFirst()
        override suspend fun update(body: UpdateUserRequest): ApiEnvelope<UserDto> =
            CompletableDeferred<ApiEnvelope<UserDto>>().also { updateCalls += it }.await()
        override suspend fun register(body: RegisterRequest): ApiEnvelope<UserDto> = error("unused")
        override suspend fun searchUsers(keyword: String, pageNum: Int, pageSize: Int): ApiEnvelope<PageDto<UserBriefDto>> = error("unused")
        override suspend fun getUser(id: Long): ApiEnvelope<UserDto> = error("unused")
    }

    private fun userDto(id: Long, nickname: String) = UserDto(id = id, nickname = nickname)

    private fun loginOk(token: String, user: UserDto) = ApiEnvelope(code = 200, data = LoginDto(token = token, user = user))

    private fun enqueueLogin(api: FakeUserApi, token: String, user: UserDto) {
        api.loginResults += loginOk(token, user)
    }

    @Test
    fun `A 的 refreshMe 迟到返回不得把 A 的资料写进 B 的会话`() = runBlocking {
        val api = FakeUserApi()
        val persistence = FakePersistence()
        val tokenHolder = TokenHolder()
        val manager = SessionManager(persistence, api, tokenHolder)

        enqueueLogin(api, "tokenA", userDto(1, "A"))
        manager.login("a", "pwd")
        val stale = launch { manager.refreshMe() }
        yieldUntilScheduled { api.meCalls.isNotEmpty() }

        manager.logout()
        enqueueLogin(api, "tokenB", userDto(2, "B"))
        manager.login("b", "pwd")

        api.meCalls[0].complete(ApiEnvelope(code = 200, data = userDto(1, "A")))
        stale.join()

        assertEquals("当前用户必须仍是 B", 2L, manager.state.value.user.id)
        assertEquals("持久化必须保持 B 的 token 配 B 的资料", "tokenB" to userDto(2, "B"), persistence.writes.last())
    }

    @Test
    fun `A 的 updateProfile 迟到返回不得写进 B 的会话`() = runBlocking {
        val api = FakeUserApi()
        val persistence = FakePersistence()
        val manager = SessionManager(persistence, api, TokenHolder())

        enqueueLogin(api, "tokenA", userDto(1, "A"))
        manager.login("a", "pwd")
        val stale = async { manager.updateProfile(UpdateUserRequest(nickname = "A 改的昵称")) }
        yieldUntilScheduled { api.updateCalls.isNotEmpty() }

        manager.logout()
        enqueueLogin(api, "tokenB", userDto(2, "B"))
        manager.login("b", "pwd")

        api.updateCalls[0].complete(ApiEnvelope(code = 200, data = userDto(1, "A 改的昵称")))
        val result = stale.await()
        assertEquals("迟到响应应按未生效处理", ApiResult.Unauthorized, result)

        assertEquals(2L, manager.state.value.user.id)
        assertEquals("tokenB" to userDto(2, "B"), persistence.writes.last())
    }

    @Test
    fun `restore 在途时被登录取代 迟到的校验结果不得覆盖 B`() = runBlocking {
        val api = FakeUserApi()
        val persistence = FakePersistence()
        val manager = SessionManager(persistence, api, TokenHolder())

        persistence.snapshotToRestore = SessionLocalStore.Snapshot(
            token = "tokenOld",
            userJson = ApiClient.json.encodeToString(UserDto.serializer(), userDto(9, "旧账号")),
        )
        val restoring = launch { manager.restore() }
        yieldUntilScheduled { api.meCalls.isNotEmpty() }

        enqueueLogin(api, "tokenB", userDto(2, "B"))
        manager.login("b", "pwd")

        api.meCalls[0].complete(ApiEnvelope(code = 200, data = userDto(9, "旧账号")))
        restoring.join()

        assertEquals(2L, manager.state.value.user.id)
        assertEquals("tokenB" to userDto(2, "B"), persistence.writes.last())
    }

    @Test
    fun `restore 旧请求失败不得清掉已经登录的 B`() = runBlocking {
        val api = FakeUserApi()
        val persistence = FakePersistence()
        val tokenHolder = TokenHolder()
        val manager = SessionManager(persistence, api, tokenHolder)

        persistence.snapshotToRestore = SessionLocalStore.Snapshot(
            token = "tokenOld",
            userJson = ApiClient.json.encodeToString(UserDto.serializer(), userDto(9, "旧账号")),
        )
        val restoring = launch { manager.restore() }
        yieldUntilScheduled { api.meCalls.isNotEmpty() }

        enqueueLogin(api, "tokenB", userDto(2, "B"))
        manager.login("b", "pwd")

        api.meCalls[0].completeExceptionally(java.io.IOException("旧请求失败"))
        restoring.join()

        assertEquals(2L, manager.state.value.user.id)
        assertEquals("tokenB", tokenHolder.token())
    }

    /** runBlocking 事件循环是协作式的：yield 让已 launch 的协程跑到第一个挂起点。 */
    private suspend fun yieldUntilScheduled(condition: () -> Boolean) {
        while (!condition()) yield()
    }
}
