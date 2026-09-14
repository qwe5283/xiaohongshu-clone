package com.xiaohongshu.app.data.api

import com.xiaohongshu.app.core.net.ApiEnvelope
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
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 互动模块：点赞 / 收藏 / 关注（契约 §4–§6）。
 *
 * 三个 toggle 接口都是**幂等翻转**：重复请求自动取消，因此客户端可乐观更新后直接调用，
 * 以响应里的 `liked`/`collected`/`followed` 作为权威值校正。
 */
interface LikeApi {

    @POST("api/like/post/{postId}")
    suspend fun togglePostLike(@Path("postId") postId: Long): ApiEnvelope<LikeToggleDto>

    @POST("api/like/comment/{commentId}")
    suspend fun toggleCommentLike(@Path("commentId") commentId: Long): ApiEnvelope<LikeToggleDto>

    @GET("api/like/status/post/{postId}")
    suspend fun postLikeStatus(@Path("postId") postId: Long): ApiEnvelope<LikeStatusDto>

    @GET("api/like/status/comment/{commentId}")
    suspend fun commentLikeStatus(@Path("commentId") commentId: Long): ApiEnvelope<LikeStatusDto>

    /** 「赞过」列表（F1 赞过 Tab，契约变更 #4），按点赞时间倒序。 */
    @GET("api/like/posts/{userId}")
    suspend fun likedPosts(
        @Path("userId") userId: Long,
        @Query("pageNum") pageNum: Int,
        @Query("pageSize") pageSize: Int = 20,
    ): ApiEnvelope<PageDto<PostDto>>
}

interface CollectApi {

    @POST("api/collect/post/{postId}")
    suspend fun toggleCollect(@Path("postId") postId: Long): ApiEnvelope<CollectToggleDto>

    @GET("api/collect/status/post/{postId}")
    suspend fun collectStatus(@Path("postId") postId: Long): ApiEnvelope<CollectStatusDto>

    /** 用户收藏列表（F1「收藏」/F2「收藏」），按收藏时间倒序。可查看他人。 */
    @GET("api/collect/posts/{userId}")
    suspend fun collectedPosts(
        @Path("userId") userId: Long,
        @Query("pageNum") pageNum: Int,
        @Query("pageSize") pageSize: Int = 20,
    ): ApiEnvelope<PageDto<PostDto>>
}

interface FollowApi {

    @POST("api/follow/{userId}")
    suspend fun toggleFollow(@Path("userId") userId: Long): ApiEnvelope<FollowToggleDto>

    @GET("api/follow/status/{userId}")
    suspend fun followStatus(@Path("userId") userId: Long): ApiEnvelope<FollowStatusDto>

    @GET("api/follow/following/{userId}")
    suspend fun following(
        @Path("userId") userId: Long,
        @Query("pageNum") pageNum: Int,
        @Query("pageSize") pageSize: Int = 20,
    ): ApiEnvelope<PageDto<FollowUserDto>>

    @GET("api/follow/followers/{userId}")
    suspend fun followers(
        @Path("userId") userId: Long,
        @Query("pageNum") pageNum: Int,
        @Query("pageSize") pageSize: Int = 20,
    ): ApiEnvelope<PageDto<FollowUserDto>>

    @GET("api/follow/count/{userId}")
    suspend fun count(@Path("userId") userId: Long): ApiEnvelope<FollowCountDto>
}
