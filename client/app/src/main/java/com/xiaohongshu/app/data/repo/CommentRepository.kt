package com.xiaohongshu.app.data.repo

import com.xiaohongshu.app.core.net.ApiResult
import com.xiaohongshu.app.core.net.apiCall
import com.xiaohongshu.app.core.net.map
import com.xiaohongshu.app.core.net.unwrap
import com.xiaohongshu.app.data.api.CommentApi
import com.xiaohongshu.app.data.api.FollowApi
import com.xiaohongshu.app.data.dto.CreateCommentRequest
import com.xiaohongshu.app.data.mapper.*
import com.xiaohongshu.app.domain.model.Comment
import com.xiaohongshu.app.domain.model.Paged
import com.xiaohongshu.app.domain.model.UserBrief

/** 评论数据源（C1 页面流 / C3 评论面板）。 */
class CommentRepository(private val commentApi: CommentApi) {

    /**
     * 一级评论（每页 20）。返回的 `total` 是**一级评论数**；
     * 「共 n 条评论」应使用 `Note.commentCount`（含回复，与之保持一致）。
     */
    suspend fun firstLevel(postId: Long, page: Int, pageSize: Int): ApiResult<Paged<Comment>> =
        apiCall {
            commentApi.firstLevel(postId = postId, pageNum = page, pageSize = pageSize).unwrap()
        }.map { it.toCommentPage() }

    /**
     * 一组回复（C3 每批 10 条，按时间正序）。
     * 「展开 N 条回复」→ 第 1 批；「展开更多回复」→ 后续批次。
     */
    suspend fun replies(commentId: Long, page: Int, pageSize: Int = BATCH_SIZE): ApiResult<Paged<Comment>> =
        apiCall {
            commentApi.replies(commentId = commentId, pageNum = page, pageSize = pageSize).unwrap()
        }.map { it.toCommentPage() }

    /** D3 发表一级评论（`parentId = 0, replyUserId = 0`）。 */
    suspend fun createFirstLevel(postId: Long, content: String): ApiResult<Comment> = apiCall {
        commentApi.create(
            CreateCommentRequest(postId = postId, content = content, parentId = 0, replyUserId = 0),
        ).unwrap()
    }.map { it.toDomain() }

    /** D3 回复某条评论（`parentId` = 一级评论 ID，`replyUserId` = 被回复者）。 */
    suspend fun createReply(
        postId: Long,
        parentId: Long,
        replyUserId: Long,
        content: String,
    ): ApiResult<Comment> = apiCall {
        commentApi.create(
            CreateCommentRequest(
                postId = postId,
                content = content,
                parentId = parentId,
                replyUserId = replyUserId,
            ),
        ).unwrap()
    }.map { it.toDomain() }

    companion object {
        /** 每批回复条数（线框 T-4c / C3 规则）。 */
        const val BATCH_SIZE = 10

        /** 一级评论每页条数。 */
        const val FIRST_LEVEL_PAGE_SIZE = 20
    }
}

/** 关注关系读写（D2；F2 关注数/粉丝数仅数字展示，无列表页）。 */
class FollowRepository(private val followApi: FollowApi) {

    suspend fun followed(userId: Long): ApiResult<Boolean> = apiCall {
        followApi.followStatus(userId).unwrap()
    }.map { it.followed }

    suspend fun following(userId: Long, page: Int, pageSize: Int): ApiResult<Paged<UserBrief>> = apiCall {
        followApi.following(userId, page, pageSize).unwrap()
    }.map { page ->
        Paged(page.records.map { it.toDomain() }, page.current.toInt(), page.total, page.pages)
    }

    suspend fun followers(userId: Long, page: Int, pageSize: Int): ApiResult<Paged<UserBrief>> = apiCall {
        followApi.followers(userId, page, pageSize).unwrap()
    }.map { page ->
        Paged(page.records.map { it.toDomain() }, page.current.toInt(), page.total, page.pages)
    }

    suspend fun counts(userId: Long): ApiResult<Pair<Long, Long>> = apiCall {
        followApi.count(userId).unwrap()
    }.map { it.followingCount to it.followersCount }
}
