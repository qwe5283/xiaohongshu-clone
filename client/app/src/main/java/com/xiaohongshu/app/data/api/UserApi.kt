package com.xiaohongshu.app.data.api

import com.xiaohongshu.app.core.net.ApiEnvelope
import com.xiaohongshu.app.data.dto.LoginDto
import com.xiaohongshu.app.data.dto.LoginRequest
import com.xiaohongshu.app.data.dto.RegisterRequest
import com.xiaohongshu.app.data.dto.UpdateUserRequest
import com.xiaohongshu.app.data.dto.UserDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** 用户模块（契约 §1）。路径不带前导 `/`，以免 baseUrl 含路径前缀时被重置。 */
interface UserApi {

    @POST("api/user/register")
    suspend fun register(@Body body: RegisterRequest): ApiEnvelope<UserDto>

    @POST("api/user/login")
    suspend fun login(@Body body: LoginRequest): ApiEnvelope<LoginDto>

    @GET("api/user/me")
    suspend fun me(): ApiEnvelope<UserDto>

    @GET("api/user/{id}")
    suspend fun getUser(@Path("id") id: Long): ApiEnvelope<UserDto>

    @PUT("api/user/update")
    suspend fun update(@Body body: UpdateUserRequest): ApiEnvelope<UserDto>
}
