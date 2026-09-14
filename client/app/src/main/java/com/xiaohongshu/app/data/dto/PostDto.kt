package com.xiaohongshu.app.data.dto

import kotlinx.serialization.Serializable

/** `PostVO`（client/docs/API契约-v2.md §2.12）。 */
@Serializable
data class PostDto(
    val id: Long = 0,
    val userId: Long = 0,
    val authorNickname: String = "",
    val authorAvatar: String = "",
    val title: String = "",
    val content: String = "",
    /** 0-图文，1-视频。后端推导。 */
    val type: Int = 0,
    val coverImage: String = "",
    val videoUrl: String = "",
    val images: List<PostImageDto> = emptyList(),
    val viewCount: Int = 0,
    val likeCount: Int = 0,
    /** = 一级评论数 + 回复数总和（契约变更 #13）。 */
    val commentCount: Int = 0,
    val collectCount: Int = 0,
    val liked: Boolean = false,
    val collected: Boolean = false,
    val followed: Boolean = false,
    val status: Int = 1,
    val createTime: String = "",
    val updateTime: String = "",
)

@Serializable
data class PostImageDto(
    val id: Long = 0,
    val imageUrl: String = "",
    val sortOrder: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
)

/** `POST /api/post/create` 请求体。 */
@Serializable
data class CreatePostRequest(
    val title: String,
    val content: String = "",
    val videoUrl: String = "",
    val imageUrls: List<String> = emptyList(),
)

/** `PUT /api/post/update` 请求体（`imageUrls` 为全量替换）。 */
@Serializable
data class UpdatePostRequest(
    val id: Long,
    val title: String? = null,
    val content: String? = null,
    val videoUrl: String? = null,
    val imageUrls: List<String>? = null,
)

/** `POST /api/post/text-image` 请求体。 */
@Serializable
data class TextImageRequest(val text: String)

/** `GET /api/post/list?sortType=` */
object SortType {
    const val LATEST = "latest"
    const val HOT = "hot"
}

/** 笔记类型筛选。`ALL` 为 null 表示不筛选（非 const，因为可空类型不能作 const）。 */
object PostType {
    val ALL: Int? = null
    const val IMAGE = 0
    const val VIDEO = 1
}
