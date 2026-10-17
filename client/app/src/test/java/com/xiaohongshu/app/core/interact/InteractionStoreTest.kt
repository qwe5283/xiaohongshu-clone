package com.xiaohongshu.app.core.interact

import com.xiaohongshu.app.core.net.ApiEnvelope
import com.xiaohongshu.app.core.ui.ToastController
import com.xiaohongshu.app.data.api.CollectApi
import com.xiaohongshu.app.data.api.FollowApi
import com.xiaohongshu.app.data.api.LikeApi
import com.xiaohongshu.app.data.dto.CollectStatusDto
import com.xiaohongshu.app.data.dto.CollectToggleDto
import com.xiaohongshu.app.data.dto.FollowCountDto
import com.xiaohongshu.app.data.dto.FollowStatusDto
import com.xiaohongshu.app.data.dto.FollowToggleDto
import com.xiaohongshu.app.data.dto.FollowUserDto
import com.xiaohongshu.app.data.dto.LikeStatusDto
import com.xiaohongshu.app.data.dto.LikeToggleDto
import com.xiaohongshu.app.data.dto.PageDto
import com.xiaohongshu.app.data.dto.PostDto
import com.xiaohongshu.app.domain.model.Note
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 会话切换时互动状态的归属回归（CodeReview-设计与可维护性-2026-10-14 发现 1，P1）。
 *
 * 注入 Unconfined dispatcher：launch 内联执行到第一个挂起点，
 * toggle 接口用 Deferred 门控制响应时序，模拟迟到响应。
 */
class InteractionStoreTest {

    /** 每次调用登记一个门并挂起，测试按需放行，模拟迟到响应。 */
    private class GatedLikeApi : LikeApi {
        val postToggles = mutableListOf<CompletableDeferred<ApiEnvelope<LikeToggleDto>>>()
        override suspend fun togglePostLike(postId: Long): ApiEnvelope<LikeToggleDto> =
            CompletableDeferred<ApiEnvelope<LikeToggleDto>>().also { postToggles += it }.await()

        override suspend fun toggleCommentLike(commentId: Long): ApiEnvelope<LikeToggleDto> = error("unused")
        override suspend fun postLikeStatus(postId: Long): ApiEnvelope<LikeStatusDto> = error("unused")
        override suspend fun commentLikeStatus(commentId: Long): ApiEnvelope<LikeStatusDto> = error("unused")
        override suspend fun likedPosts(userId: Long, pageNum: Int, pageSize: Int): ApiEnvelope<PageDto<PostDto>> = error("unused")
    }

    private class UnusedCollectApi : CollectApi {
        override suspend fun toggleCollect(postId: Long): ApiEnvelope<CollectToggleDto> = error("unused")
        override suspend fun collectStatus(postId: Long): ApiEnvelope<CollectStatusDto> = error("unused")
        override suspend fun collectedPosts(userId: Long, pageNum: Int, pageSize: Int): ApiEnvelope<PageDto<PostDto>> = error("unused")
    }

    private class UnusedFollowApi : FollowApi {
        override suspend fun toggleFollow(userId: Long): ApiEnvelope<FollowToggleDto> = error("unused")
        override suspend fun followStatus(userId: Long): ApiEnvelope<FollowStatusDto> = error("unused")
        override suspend fun following(userId: Long, pageNum: Int, pageSize: Int): ApiEnvelope<PageDto<FollowUserDto>> = error("unused")
        override suspend fun followers(userId: Long, pageNum: Int, pageSize: Int): ApiEnvelope<PageDto<FollowUserDto>> = error("unused")
        override suspend fun count(userId: Long): ApiEnvelope<FollowCountDto> = error("unused")
    }

    private val unconfined: CoroutineDispatcher = Dispatchers.Unconfined

    private fun newStore(likeApi: LikeApi): InteractionStore =
        InteractionStore(likeApi, UnusedCollectApi(), UnusedFollowApi(), ToastController(), dispatcher = unconfined)

    private fun note(id: Long = 1L, liked: Boolean = false, likeCount: Int = 0) = Note(
        id = id,
        authorId = 2L,
        authorNickname = "作者",
        authorAvatar = "",
        title = "标题",
        content = "正文",
        isVideo = false,
        coverUrl = "",
        videoUrl = "",
        images = emptyList(),
        viewCount = 0,
        likeCount = likeCount,
        commentCount = 0,
        collectCount = 0,
        liked = liked,
    )

    @Test
    fun `clear 后上一会话的迟到响应不得写入新会话覆盖量`() {
        val likeApi = GatedLikeApi()
        val store = newStore(likeApi)
        val target = note()

        store.toggleLike(target) // 会话 A：epoch=1，响应在途
        store.clear() // 退出登录：清空覆盖量与序号表
        store.toggleLike(target) // 会话 B：同一目标，序号重新从 1 开始

        likeApi.postToggles[1].complete(ApiEnvelope(code = 200, data = LikeToggleDto(liked = true)))
        val afterNew = store.noteOverrides.value[target.id]?.liked
        assertTrue("新会话的权威响应应生效", afterNew == true)

        likeApi.postToggles[0].complete(ApiEnvelope(code = 200, data = LikeToggleDto(liked = false)))
        assertTrue(
            "上一会话的迟到响应必须整体丢弃，不得覆盖新会话状态",
            store.noteOverrides.value[target.id]?.liked == true,
        )
    }

    @Test
    fun `clear 清空上一会话的覆盖量`() {
        val likeApi = GatedLikeApi()
        val store = newStore(likeApi)

        store.toggleLike(note())
        assertTrue(store.noteOverrides.value.isNotEmpty())

        store.clear()
        assertTrue(store.noteOverrides.value.isEmpty())
    }
}
