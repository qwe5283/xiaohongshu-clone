package com.xiaohongshu.app.data.dto

import kotlinx.serialization.Serializable

/**
 * MyBatis-Plus `IPage` 形状。
 *
 * **注意字段名是 `size`/`current`，不是 `pageSize`/`pageNum`**（请求参数才是 pageNum/pageSize）。
 * 见 client/docs/API契约-v2.md 通用约定。
 */
@Serializable
data class PageDto<T>(
    val records: List<T> = emptyList(),
    val total: Long = 0,
    val size: Long = 20,
    val current: Long = 1,
    val pages: Long = 0,
)

/** `POST /api/like/post/{id}`、`/api/collect/post/{id}` */
@Serializable
data class LikeToggleDto(val liked: Boolean = false, val message: String = "")

@Serializable
data class CollectToggleDto(val collected: Boolean = false, val message: String = "")

/** `POST /api/follow/{userId}` */
@Serializable
data class FollowToggleDto(val followed: Boolean = false, val message: String = "")

/** `GET /api/like/status/post/{id}` 等状态查询。 */
@Serializable
data class LikeStatusDto(val liked: Boolean = false)

@Serializable
data class CollectStatusDto(val collected: Boolean = false)

@Serializable
data class FollowStatusDto(val followed: Boolean = false)

/** `GET /api/follow/count/{userId}` */
@Serializable
data class FollowCountDto(
    val followingCount: Long = 0,
    val followersCount: Long = 0,
)

/** `GET /api/follow/following/{userId}` 与 `/followers/{userId}` 的元素。 */
@Serializable
data class FollowUserDto(
    val id: Long = 0,
    val nickname: String = "",
    val avatar: String = "",
    val bio: String = "",
    val followTime: String = "",
    val followed: Boolean = false,
)

/** `GET /api/upload/image` 等上传响应。 */
@Serializable
data class UploadResultDto(val url: String = "")

/** `POST /api/post/text-image` */
@Serializable
data class TextImageDto(
    val url: String = "",
    val width: Int = 0,
    val height: Int = 0,
)

/** `GET /api/post/hot-keywords` */
typealias KeywordListDto = List<String>
