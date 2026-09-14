package com.xiaohongshu.app.data.repo

import com.xiaohongshu.app.core.net.ApiResult
import com.xiaohongshu.app.core.net.apiCall
import com.xiaohongshu.app.core.net.map
import com.xiaohongshu.app.core.net.unwrap
import com.xiaohongshu.app.data.api.CollectApi
import com.xiaohongshu.app.data.api.LikeApi
import com.xiaohongshu.app.data.api.PostApi
import com.xiaohongshu.app.data.dto.CreatePostRequest
import com.xiaohongshu.app.data.dto.SortType
import com.xiaohongshu.app.data.dto.TextImageRequest
import com.xiaohongshu.app.data.dto.UpdatePostRequest
import com.xiaohongshu.app.data.mapper.*
import com.xiaohongshu.app.domain.model.Note
import com.xiaohongshu.app.domain.model.Paged

/**
 * 笔记数据源。
 *
 * 只负责「取数 + 映射」，不做互动状态合并（那是 [com.xiaohongshu.app.core.interact.InteractionStore]
 * 的职责），也不持有界面状态。
 */
class PostRepository(
    private val postApi: PostApi,
    private val likeApi: LikeApi,
    private val collectApi: CollectApi,
) {

    /** B1 发现流 / B3 搜索结果 / B5 关注流。[keyword] 为标题关键词。 */
    suspend fun feed(
        page: Int,
        pageSize: Int,
        keyword: String? = null,
        type: Int? = null,
        sortType: String? = null,
    ): ApiResult<Paged<Note>> = apiCall {
        postApi.list(
            pageNum = page,
            pageSize = pageSize,
            keyword = keyword,
            type = type,
            sortType = sortType,
        ).unwrap()
    }.map { it.toNotePage() }

    /** B5 关注流：仅已关注作者的已发布笔记。 */
    suspend fun followingFeed(page: Int, pageSize: Int): ApiResult<Paged<Note>> = apiCall {
        postApi.followingFeed(pageNum = page, pageSize = pageSize).unwrap()
    }.map { it.toNotePage() }

    /** F2 他人主页「笔记」Tab。 */
    suspend fun userPosts(userId: Long, page: Int, pageSize: Int): ApiResult<Paged<Note>> = apiCall {
        postApi.userPosts(userId = userId, pageNum = page, pageSize = pageSize).unwrap()
    }.map { it.toNotePage() }

    /** F1「笔记」Tab。 */
    suspend fun myPosts(page: Int, pageSize: Int): ApiResult<Paged<Note>> = apiCall {
        postApi.myPosts(pageNum = page, pageSize = pageSize).unwrap()
    }.map { it.toNotePage() }

    /** F1「赞过」Tab / F2「赞过」不可见（他人主页仅笔记/收藏）。 */
    suspend fun likedPosts(userId: Long, page: Int, pageSize: Int): ApiResult<Paged<Note>> = apiCall {
        likeApi.likedPosts(userId = userId, pageNum = page, pageSize = pageSize).unwrap()
    }.map { it.toNotePage() }

    /** F1「收藏」/ F2「收藏」Tab，按收藏时间倒序。 */
    suspend fun collectedPosts(userId: Long, page: Int, pageSize: Int): ApiResult<Paged<Note>> = apiCall {
        collectApi.collectedPosts(userId = userId, pageNum = page, pageSize = pageSize).unwrap()
    }.map { it.toNotePage() }

    /** C1/C2 详情。注意：每次访问后端会 +1 浏览量。 */
    suspend fun detail(postId: Long): ApiResult<Note> = apiCall {
        postApi.detail(postId).unwrap()
    }.map { it.toDomain() }

    /** E6：创建笔记（媒体 URL 已先通过 upload 模块拿到）。 */
    suspend fun create(
        title: String,
        content: String,
        imageUrls: List<String>,
        videoUrl: String = "",
    ): ApiResult<Note> = apiCall {
        postApi.create(
            CreatePostRequest(
                title = title,
                content = content,
                videoUrl = videoUrl,
                imageUrls = imageUrls,
            ),
        ).unwrap()
    }.map { it.toDomain() }

    suspend fun update(
        postId: Long,
        title: String? = null,
        content: String? = null,
        imageUrls: List<String>? = null,
    ): ApiResult<Note> = apiCall {
        postApi.update(
            UpdatePostRequest(id = postId, title = title, content = content, imageUrls = imageUrls),
        ).unwrap()
    }.map { it.toDomain() }

    suspend fun delete(postId: Long): ApiResult<Unit> = apiCall {
        postApi.delete(postId).unwrap()
    }

    /** B2「猜你想搜」。 */
    suspend fun hotKeywords(): ApiResult<List<String>> = apiCall { postApi.hotKeywords().unwrap() }

    /**
     * E3 文字配图：后端生成 2:3 PNG 并返回可直接入库的 URL。
     */
    suspend fun generateTextImage(text: String): ApiResult<String> = apiCall {
        postApi.generateTextImage(TextImageRequest(text)).unwrap()
    }.map { it.url }

    companion object {
        /** 首页默认排序。 */
        const val DEFAULT_SORT = SortType.LATEST
    }
}
