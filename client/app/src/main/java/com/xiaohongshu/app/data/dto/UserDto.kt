package com.xiaohongshu.app.data.dto

import kotlinx.serialization.Serializable

/**
 * `UserVO`（client/docs/API契约-v2.md §1.7）。
 *
 * 计数语义易混淆，务必区分：
 * - [likeCount] / [collectCount] = **收到**的赞/收藏（获赞数/获藏数），客户端暂不展示；
 * - [likeAndCollectCount] = 收到的获赞与收藏总数 → F1 统计行第三项；
 * - [collectedPostCount] = **我**收藏的笔记数 → F1 小组件卡「收藏」；
 * - [likedPostCount] = **我**赞过的笔记数 → F1 小组件卡「赞过」。
 */
@Serializable
data class UserDto(
    val id: Long = 0,
    val username: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val gender: Int = 0,
    val phone: String = "",
    val email: String = "",
    val bio: String = "",
    val backgroundImage: String = "",
    val birthday: String = "",
    val region: String = "",
    val occupation: String = "",
    val school: String = "",
    val redId: String = "",
    val followingCount: Long = 0,
    val followersCount: Long = 0,
    val likeCount: Long = 0,
    val collectCount: Long = 0,
    val likeAndCollectCount: Long = 0,
    val collectedPostCount: Long = 0,
    val likedPostCount: Long = 0,
    val createTime: String = "",
)

/** `POST /api/user/register` 请求体。 */
@Serializable
data class RegisterRequest(
    val username: String,
    val password: String,
    val nickname: String? = null,
    val phone: String? = null,
)

/** `POST /api/user/login` 请求体。 */
@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

/** `POST /api/user/login` 响应。 */
@Serializable
data class LoginDto(
    val token: String = "",
    val expiresIn: Long = 0,
    val user: UserDto = UserDto(),
)

/**
 * `PUT /api/user/update` 请求体。仅提交有变化的字段。
 *
 * 用可空 + [kotlinx.serialization.json.Json] 的 `explicitNulls = false`
 * 保证未设置的字段不会出现在请求体里（后端按「可选」处理）。
 */
@Serializable
data class UpdateUserRequest(
    val nickname: String? = null,
    val avatar: String? = null,
    val gender: Int? = null,
    val email: String? = null,
    val bio: String? = null,
    val backgroundImage: String? = null,
    val birthday: String? = null,
    val region: String? = null,
    val occupation: String? = null,
    val school: String? = null,
)
