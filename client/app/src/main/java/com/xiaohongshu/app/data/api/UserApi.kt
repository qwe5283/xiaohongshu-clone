package com.xiaohongshu.app.data.api

import com.xiaohongshu.app.core.net.ApiEnvelope
import com.xiaohongshu.app.data.dto.LoginDto
import com.xiaohongshu.app.data.dto.LoginRequest
import com.xiaohongshu.app.data.dto.PageDto
import com.xiaohongshu.app.data.dto.RegisterRequest
import com.xiaohongshu.app.data.dto.UpdateUserRequest
import com.xiaohongshu.app.data.dto.UserBriefDto
import com.xiaohongshu.app.data.dto.UserDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** 用户模块（契约 §1）。路径不带前导 `/`，以免 baseUrl 含路径前缀时被重置。 */
interface UserApi {

    @POST("api/user/register")
    suspend fun register(@Body body: RegisterRequest): ApiEnvelope<UserDto>

    @POST("api/user/login")
    suspend fun login(@Body body: LoginRequest): ApiEnvelope<LoginDto>

    @GET("api/user/me")
    suspend fun me(): ApiEnvelope<UserDto>

    /** B3-1「用户」页签（契约 §1.8）：昵称/小红书号/登录账号模糊搜索，带 token 时填充 `followed`。 */
    @GET("api/user/search")
    suspend fun searchUsers(
        @Query("keyword") keyword: String,
        @Query("pageNum") pageNum: Int,
        @Query("pageSize") pageSize: Int = 20,
    ): ApiEnvelope<PageDto<UserBriefDto>>

    @GET("api/user/{id}")
    suspend fun getUser(@Path("id") id: Long): ApiEnvelope<UserDto>

    @PUT("api/user/update")
    suspend fun update(@Body body: UpdateUserRequest): ApiEnvelope<UserDto>
}
