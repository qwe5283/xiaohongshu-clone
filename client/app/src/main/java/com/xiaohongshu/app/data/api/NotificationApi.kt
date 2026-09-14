package com.xiaohongshu.app.data.api

import com.xiaohongshu.app.core.net.ApiEnvelope
import com.xiaohongshu.app.data.dto.NotificationDto
import com.xiaohongshu.app.data.dto.PageDto
import com.xiaohongshu.app.data.dto.UnreadCountDto
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** 消息通知模块（契约 §7）。全部需登录。 */
interface NotificationApi {

    /**
     * 未读数（15s 轮询，仅登录态 + 前台）。
     * 契约变更 #6：同时返回三个分类未读数，供 G1 三入口卡角标；为 0 时客户端隐藏角标。
     */
    @GET("api/notification/unread-count")
    suspend fun unreadCount(): ApiEnvelope<UnreadCountDto>

    /**
     * 通知列表。G2/G3/G4 用 [category]（1/2/3）；[type] 为更细的类型筛选。
     * 契约变更 #7。
     */
    @GET("api/notification/list")
    suspend fun list(
        @Query("pageNum") pageNum: Int,
        @Query("pageSize") pageSize: Int = 20,
        @Query("category") category: Int? = null,
        @Query("type") type: Int? = null,
    ): ApiEnvelope<PageDto<NotificationDto>>

    /** 点条目时先标已读（角标 -1）再跳转。失败不阻断操作，下次轮询校正。 */
    @PUT("api/notification/read/{id}")
    suspend fun markRead(@Path("id") id: Long): ApiEnvelope<Unit>

    /** G6 一键已读。[category] 限定范围，不传 = 全部。 */
    @PUT("api/notification/read-all")
    suspend fun markAllRead(@Query("category") category: Int? = null): ApiEnvelope<Unit>
}
