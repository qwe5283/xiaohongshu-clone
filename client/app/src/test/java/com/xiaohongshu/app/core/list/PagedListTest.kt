package com.xiaohongshu.app.core.list

import com.xiaohongshu.app.core.net.ApiResult
import com.xiaohongshu.app.domain.model.Paged
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 分页请求生命周期回归（CodeReview-设计与可维护性-2026-10-14 发现 2，P1）。
 *
 * 每条用例对应审查验证表里的一个场景。fetch 用 [CompletableDeferred] 精确控制
 * 返回时序：请求挂起在门上，测试按任意顺序放行，模拟乱序网络与取消。
 */
class PagedListTest {

    private data class Row(val id: Long)

    /** 记录每次 fetch 调用并挂起，直到测试放行对应 Deferred。 */
    private class GatedFetch {
        val calls = mutableListOf<CompletableDeferred<ApiResult<Paged<Row>>>>()
        val fetch: suspend (Int, Int) -> ApiResult<Paged<Row>> = { _, _ ->
            CompletableDeferred<ApiResult<Paged<Row>>>().also { calls += it }.await()
        }
    }

    private fun ok(vararg ids: Long, hasMore: Boolean = true): ApiResult<Paged<Row>> = ApiResult.Ok(
        Paged(ids.map { Row(it) }, page = 1, total = ids.size.toLong(), pages = if (hasMore) 2 else 1),
    )

    private fun newList(gate: GatedFetch) = PagedList(keyOf = { it.id }, fetch = gate.fetch)

    private fun PagedList<Row>.ids(): List<Long> = state.value.items.map { it.id }

    @Test
    fun `两次刷新按相反顺序返回 旧响应不得覆盖新结果`() = runBlocking {
        val gate = GatedFetch()
        val list = newList(gate)
        val first = launch { list.refresh() }
        val second = launch { list.refresh() }
        yieldUntilScheduled(gate, expectedCalls = 2)

        gate.calls[1].complete(ok(2L)) // 新意图先返回
        second.join()
        gate.calls[0].complete(ok(1L)) // 旧意图后返回
        first.join()

        assertEquals(listOf(2L), list.ids())
    }

    @Test
    fun `refresh 在途时 resetTo 旧响应不得回写`() = runBlocking {
        val gate = GatedFetch()
        val list = newList(gate)
        val job = launch { list.refresh() }
        yieldUntilScheduled(gate, expectedCalls = 1)

        list.resetTo(listOf(Row(9)))
        gate.calls[0].complete(ok(1L))
        job.join()

        assertEquals(listOf(9L), list.ids())
    }

    @Test
    fun `refresh 取消后加载标志不得残留阻塞追加`() = runBlocking {
        val gate = GatedFetch()
        val list = newList(gate)
        val job = launch { list.refresh() }
        yieldUntilScheduled(gate, expectedCalls = 1)

        job.cancel()
        job.join()
        assertEquals(false, list.state.value.loading)

        val append = launch { list.loadMore() }
        yieldUntilScheduled(gate, expectedCalls = 2)
        gate.calls[1].complete(ok(2L))
        append.join()

        assertEquals(listOf(2L), list.ids())
    }

    @Test
    fun `loadMore 在途时 resetTo 追加不得写入旧列表`() = runBlocking {
        val gate = GatedFetch()
        val list = newList(gate)
        list.resetTo(listOf(Row(1)))
        val job = launch { list.loadMore() }
        yieldUntilScheduled(gate, expectedCalls = 1)

        list.resetTo(listOf(Row(7)))
        gate.calls[0].complete(ok(2L))
        job.join()

        assertEquals(listOf(7L), list.ids())
        assertEquals(1, list.state.value.page)
    }

    @Test
    fun `刷新结束时追加仍在途 不得让新的追加抢跑`() = runBlocking {
        val gate = GatedFetch()
        val list = newList(gate)
        list.resetTo(listOf(Row(1)))

        val append = launch { list.loadMore() }
        yieldUntilScheduled(gate, expectedCalls = 1)
        val refresh = launch { list.refresh() }
        yieldUntilScheduled(gate, expectedCalls = 2)

        gate.calls[1].complete(ok(9L))
        refresh.join()

        // 刷新已返回，但旧追加尚未结束；不能再发第二个追加请求。
        val blocked = launch { list.loadMore() }
        yield()
        assertEquals(2, gate.calls.size)
        blocked.join()

        gate.calls[0].complete(ok(2L))
        append.join()
        val next = launch { list.loadMore() }
        yieldUntilScheduled(gate, expectedCalls = 3)
        gate.calls[2].complete(ok(10L))
        next.join()
        assertEquals(false, list.state.value.loadingMore)
    }

    /** runBlocking 事件循环是协作式的：yield 让已 launch 的协程跑到第一个挂起点。 */
    private suspend fun yieldUntilScheduled(gate: GatedFetch, expectedCalls: Int) {
        while (gate.calls.size < expectedCalls) yield()
    }
}
