package com.xiaohongshu.app.data.api

import com.xiaohongshu.app.core.net.ApiEnvelope
import com.xiaohongshu.app.data.dto.CreatePostRequest
import com.xiaohongshu.app.data.dto.PageDto
import com.xiaohongshu.app.data.dto.PostDto
import com.xiaohongshu.app.data.dto.TextImageDto
import com.xiaohongshu.app.data.dto.TextImageRequest
import com.xiaohongshu.app.data.dto.UpdatePostRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** 笔记模块（契约 §2）。所有列表统一 pageSize=20（调用方显式传参）。 */
interface PostApi {

    @POST("api/post/create")
    suspend fun create(@Body body: CreatePostRequest): ApiEnvelope<PostDto>

    @PUT("api/post/update")
    suspend fun update(@Body body: UpdatePostRequest): ApiEnvelope<PostDto>

    @DELETE("api/post/delete/{postId}")
    suspend fun delete(@Path("postId") postId: Long): ApiEnvelope<Unit>

    /** 每次访问后端自动 +1 浏览量。 */
    @GET("api/post/{postId}")
    suspend fun detail(@Path("postId") postId: Long): ApiEnvelope<PostDto>

    /** 发现流 / 搜索结果（B1、B3）。[keyword] 为标题关键词。 */
    @GET("api/post/list")
    suspend fun list(
        @Query("pageNum") pageNum: Int,
        @Query("pageSize") pageSize: Int = 20,
        @Query("keyword") keyword: String? = null,
        @Query("type") type: Int? = null,
        @Query("sortType") sortType: String? = null,
    ): ApiEnvelope<PageDto<PostDto>>

    /** 某用户的笔记（F2「笔记」Tab）。 */
    @GET("api/post/user/{userId}")
    suspend fun userPosts(
        @Path("userId") userId: Long,
        @Query("pageNum") pageNum: Int,
        @Query("pageSize") pageSize: Int = 20,
        @Query("sortType") sortType: String? = null,
    ): ApiEnvelope<PageDto<PostDto>>

    /** 我的笔记（F1「笔记」Tab）。 */
    @GET("api/post/my")
    suspend fun myPosts(
        @Query("pageNum") pageNum: Int,
        @Query("pageSize") pageSize: Int = 20,
        @Query("sortType") sortType: String? = null,
    ): ApiEnvelope<PageDto<PostDto>>

    /** 关注流（B5，契约变更 #3）：仅已关注作者的已发布笔记，时间倒序。 */
    @GET("api/post/following-feed")
    suspend fun followingFeed(
        @Query("pageNum") pageNum: Int,
        @Query("pageSize") pageSize: Int = 20,
    ): ApiEnvelope<PageDto<PostDto>>

    /** 猜你想搜词条（B2，契约变更 #5）。 */
    @GET("api/post/hot-keywords")
    suspend fun hotKeywords(): ApiEnvelope<List<String>>

    /**
     * 文字配图（E3，契约变更 #12）：后端生成 2:3 PNG 并落库，直接返回可用的 URL，
     * 免去客户端「取字节流 → 再上传」的双跳。
     */
    @POST("api/post/text-image")
    suspend fun generateTextImage(@Body body: TextImageRequest): ApiEnvelope<TextImageDto>
}
