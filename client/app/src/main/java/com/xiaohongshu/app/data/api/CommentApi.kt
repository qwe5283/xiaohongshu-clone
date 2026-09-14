package com.xiaohongshu.app.data.api

import com.xiaohongshu.app.core.net.ApiEnvelope
import com.xiaohongshu.app.data.dto.CommentDto
import com.xiaohongshu.app.data.dto.CreateCommentRequest
import com.xiaohongshu.app.data.dto.PageDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** 评论模块（契约 §3）。 */
interface CommentApi {

    /** 发表评论 / 回复。一级评论 `parentId=0, replyUserId=0`。 */
    @POST("api/comment/create")
    suspend fun create(@Body body: CreateCommentRequest): ApiEnvelope<CommentDto>

    /** 线框未设删除入口（#2），客户端不调用；契约保留。 */
    @DELETE("api/comment/delete/{commentId}")
    suspend fun delete(@Path("commentId") commentId: Long): ApiEnvelope<Unit>

    /**
     * 一级评论列表（C1-1 页面流 / C3 面板）。
     * `total` = 一级评论数；「共 n 条评论」请用 `PostVO.commentCount`（含回复）。
     */
    @GET("api/comment/post/{postId}")
    suspend fun firstLevel(
        @Path("postId") postId: Long,
        @Query("pageNum") pageNum: Int,
        @Query("pageSize") pageSize: Int = 20,
    ): ApiEnvelope<PageDto<CommentDto>>

    /** 回复列表（C3-1/C3-4/C3-5）：每批拉取 10 条，时间正序。 */
    @GET("api/comment/replies/{commentId}")
    suspend fun replies(
        @Path("commentId") commentId: Long,
        @Query("pageNum") pageNum: Int,
        @Query("pageSize") pageSize: Int = 10,
    ): ApiEnvelope<PageDto<CommentDto>>
}
