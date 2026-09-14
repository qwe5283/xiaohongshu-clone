package com.xiaohongshu.app.data.repo

import com.xiaohongshu.app.core.net.ApiResult
import com.xiaohongshu.app.core.net.apiCall
import com.xiaohongshu.app.core.net.map
import com.xiaohongshu.app.core.net.unwrap
import com.xiaohongshu.app.data.api.UserApi
import com.xiaohongshu.app.data.mapper.*
import com.xiaohongshu.app.domain.model.Paged
import com.xiaohongshu.app.domain.model.User
import com.xiaohongshu.app.domain.model.UserBrief

/**
 * 用户资料读取。
 *
 * 存在的理由：F2 他人主页需要 `GET /api/user/{id}` 才能拿到对方的简介、头图、性别、
 * 获赞与收藏等字段——这些无法从笔记列表的 `authorNickname/authorAvatar` 兜底出来。
 * 登录态自身的读写仍由 [com.xiaohongshu.app.data.local.SessionManager] 承担
 * （token 生命周期与用户资料刷新耦合在一起，不在这里重复）。
 */
class UserRepository(private val userApi: UserApi) {

    /** 他人（或自己）资料。公开接口，游客也可读。 */
    suspend fun getUser(userId: Long): ApiResult<User> = apiCall {
        userApi.getUser(userId).unwrap()
    }.map { it.toDomain() }
}
