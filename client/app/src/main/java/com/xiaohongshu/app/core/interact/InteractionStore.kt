package com.xiaohongshu.app.core.interact

import com.xiaohongshu.app.core.net.ApiResult
import com.xiaohongshu.app.core.net.Code
import com.xiaohongshu.app.core.net.userMessage
import com.xiaohongshu.app.core.ui.ToastController
import com.xiaohongshu.app.data.api.CollectApi
import com.xiaohongshu.app.data.api.FollowApi
import com.xiaohongshu.app.data.api.LikeApi
import com.xiaohongshu.app.core.net.apiCall
import com.xiaohongshu.app.core.net.unwrap
import com.xiaohongshu.app.domain.model.Comment
import com.xiaohongshu.app.domain.model.Note
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 某个笔记的本地互动覆盖量。
 *
 * [liked] / [collected] 为 null 表示「无覆盖，用服务端值」；
 * [likeDelta] / [collectDelta] 是相对**服务端计数**的增量，故 显示值 = 服务端值 + delta。
 * 用增量而非绝对值，能让乐观更新在「服务端计数随时间变化」时依然自洽。
 */
data class NoteInteraction(
    val liked: Boolean? = null,
    val collected: Boolean? = null,
    val likeDelta: Int = 0,
    val collectDelta: Int = 0,
)

/** 评论点赞的本地覆盖（同 [NoteInteraction] 的思路，只是没有收藏）。 */
data class CommentInteraction(
    val liked: Boolean? = null,
    val likeDelta: Int = 0,
)

/**
 * 全局互动状态中心（D1/D2 状态机的唯一实现）。
 *
 * 为什么需要它：同一个笔记会同时出现在首页卡片、搜索结果、个人主页、详情页；
 * 同一个作者会出现在详情作者栏与他人主页。若各页各自维护状态，从详情返回列表后
 * 赞数会「跳回去」。这里用一份 overrides 让所有页面读同一份真相。
 *
 * 语义（线框 D1/D2）：
 * - **乐观更新**：点击立即改 UI，再发请求；失败**回滚**并可能伴随全局错误 Toast（I2）；
 * - **幂等容错**：toggle 接口的 6001/6002/7001/7002 视为「已达目标状态」，不弹错；
 * - 游客点击不进入本类——由调用方先做登录拦截（A2）。
 */
class InteractionStore(
    private val likeApi: LikeApi,
    private val collectApi: CollectApi,
    private val followApi: FollowApi,
    private val toasts: ToastController,
) {

    private val _noteOverrides = MutableStateFlow<Map<Long, NoteInteraction>>(emptyMap())
    val noteOverrides: StateFlow<Map<Long, NoteInteraction>> = _noteOverrides.asStateFlow()

    private val _followOverrides = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    val followOverrides: StateFlow<Map<Long, Boolean>> = _followOverrides.asStateFlow()

    private val _commentOverrides = MutableStateFlow<Map<Long, CommentInteraction>>(emptyMap())
    val commentOverrides: StateFlow<Map<Long, CommentInteraction>> = _commentOverrides.asStateFlow()

    // ------------------------------------------------------------------ 读取合并

    /** 把本地乐观覆盖合并进服务端返回的笔记。UI 一律渲染本函数的返回值。 */
    fun merge(note: Note): Note {
        val ov = _noteOverrides.value[note.id] ?: return note
        return note.copy(
            liked = ov.liked ?: note.liked,
            collected = ov.collected ?: note.collected,
            likeCount = (note.likeCount + ov.likeDelta).coerceAtLeast(0),
            collectCount = (note.collectCount + ov.collectDelta).coerceAtLeast(0),
        )
    }

    fun mergeAll(notes: List<Note>): List<Note> = if (_noteOverrides.value.isEmpty()) notes else notes.map(::merge)

    /** 作者关注态合并（详情作者栏 / 他人主页）。 */
    fun followedOf(authorId: Long, serverValue: Boolean): Boolean =
        _followOverrides.value[authorId] ?: serverValue

    /** 评论点赞态合并。 */
    fun merge(comment: Comment): Comment {
        val ov = _commentOverrides.value[comment.id] ?: return comment
        return comment.copy(
            liked = ov.liked ?: comment.liked,
            likeCount = (comment.likeCount + ov.likeDelta).coerceAtLeast(0),
        )
    }

    fun mergeComments(comments: List<Comment>): List<Comment> =
        if (_commentOverrides.value.isEmpty()) comments else comments.map(::merge)

    // ------------------------------------------------------------------ 点赞笔记

    /**
     * D1 点赞/取消（乐观 + 回滚）。返回最终权威状态；失败返回 null 表示已回滚。
     *
     * **[serverNote] 必须是服务端原值，不能是 [merge] 的返回值。**
     * 本方法用 `serverNote.likeCount` 作为计数基准、`serverNote.liked` 作为状态基准来计算增量；
     * 若传入已合并过的值，合并后的 count 会被当成基准重复叠加，导致计数永久偏移。
     * 正确写法：渲染用 `merge(raw)`，变更用 `toggleLike(raw)`（见开发规范 §4.3）。
     */
    suspend fun toggleLike(serverNote: Note): Boolean? {
        val before = _noteOverrides.value[serverNote.id] ?: NoteInteraction()
        val displayedLiked = before.liked ?: serverNote.liked
        val intent = !displayedLiked

        // 乐观：立即翻转，并按意图预调 ±1
        putNote(serverNote.id, before.copy(liked = intent, likeDelta = before.likeDelta + if (intent) 1 else -1))

        return when (val r = apiCall { likeApi.togglePostLike(serverNote.id).unwrap() }) {
            is ApiResult.Ok -> {
                val authoritative = r.data.liked
                // 以「服务端基准值 serverNote.liked」重算增量，天然自纠正竞态与重复点击
                val delta = deltaFor(authoritative, serverNote.liked, before.likeDelta)
                putNote(serverNote.id, before.copy(liked = authoritative, likeDelta = delta))
                // 服务端返回的 message（"点赞成功"/"取消点赞成功"）不需要每次 Toast，保持原版静默体验
                authoritative
            }
            is ApiResult.Biz -> {
                if (r.code in IDEMPOTENT_CODES) {
                    putNote(serverNote.id, before.copy(liked = intent, likeDelta = deltaFor(intent, serverNote.liked, before.likeDelta)))
                    intent
                } else {
                    putNote(serverNote.id, before)
                    toasts.show(r.userMessage())
                    null
                }
            }
            else -> {
                putNote(serverNote.id, before)
                toasts.show(r.userMessage())
                null
            }
        }
    }

    // ------------------------------------------------------------------ 收藏笔记

    /**
     * D1 收藏/取消。收藏同时影响「我页-收藏」Tab 内容（列表回到前台时需刷新）。
     * **[serverNote] 传服务端原值**，理由同 [toggleLike]。
     */
    suspend fun toggleCollect(serverNote: Note): Boolean? {
        val before = _noteOverrides.value[serverNote.id] ?: NoteInteraction()
        val displayed = before.collected ?: serverNote.collected
        val intent = !displayed

        putNote(serverNote.id, before.copy(collected = intent, collectDelta = before.collectDelta + if (intent) 1 else -1))

        return when (val r = apiCall { collectApi.toggleCollect(serverNote.id).unwrap() }) {
            is ApiResult.Ok -> {
                val authoritative = r.data.collected
                putNote(
                    serverNote.id,
                    before.copy(
                        collected = authoritative,
                        collectDelta = deltaFor(authoritative, serverNote.collected, before.collectDelta),
                    ),
                )
                authoritative
            }
            is ApiResult.Biz -> {
                if (r.code in IDEMPOTENT_CODES) {
                    putNote(serverNote.id, before.copy(collected = intent, collectDelta = deltaFor(intent, serverNote.collected, before.collectDelta)))
                    intent
                } else {
                    putNote(serverNote.id, before)
                    toasts.show(r.userMessage())
                    null
                }
            }
            else -> {
                putNote(serverNote.id, before)
                toasts.show(r.userMessage())
                null
            }
        }
    }

    // ------------------------------------------------------------------ 关注作者

    /**
     * D2 关注/取关。详情作者栏（描边胶囊）与他人主页（通栏大按钮）共用同一状态机。
     * 自己的笔记/主页不显示关注按钮，调用方负责不调用。
     */
    suspend fun toggleFollow(authorId: Long, serverValue: Boolean): Boolean? {
        val before = _followOverrides.value[authorId]
        val displayed = before ?: serverValue
        val intent = !displayed
        putFollow(authorId, intent)

        return when (val r = apiCall { followApi.toggleFollow(authorId).unwrap() }) {
            is ApiResult.Ok -> {
                putFollow(authorId, r.data.followed)
                r.data.followed
            }
            is ApiResult.Biz -> {
                if (r.code in IDEMPOTENT_CODES || r.code == Code.CANNOT_FOLLOW_SELF) {
                    if (r.code == Code.CANNOT_FOLLOW_SELF) {
                        putFollow(authorId, before)
                        toasts.show("不能关注自己")
                        null
                    } else {
                        putFollow(authorId, intent)
                        intent
                    }
                } else {
                    putFollow(authorId, before)
                    toasts.show(r.userMessage())
                    null
                }
            }
            else -> {
                putFollow(authorId, before)
                toasts.show(r.userMessage())
                null
            }
        }
    }

    // ------------------------------------------------------------------ 点赞评论

    /**
     * 图文页流评论 ♥ / 视频评论面板内评论 ♥ 共用。
     * **[serverComment] 传服务端原值**，理由同 [toggleLike]。
     */
    suspend fun toggleCommentLike(serverComment: Comment): Boolean? {
        val before = _commentOverrides.value[serverComment.id] ?: CommentInteraction()
        val displayed = before.liked ?: serverComment.liked
        val intent = !displayed

        putComment(serverComment.id, before.copy(liked = intent, likeDelta = before.likeDelta + if (intent) 1 else -1))

        return when (val r = apiCall { likeApi.toggleCommentLike(serverComment.id).unwrap() }) {
            is ApiResult.Ok -> {
                val authoritative = r.data.liked
                putComment(
                    serverComment.id,
                    before.copy(
                        liked = authoritative,
                        likeDelta = deltaFor(authoritative, serverComment.liked, before.likeDelta),
                    ),
                )
                authoritative
            }
            is ApiResult.Biz -> {
                if (r.code in IDEMPOTENT_CODES) {
                    putComment(serverComment.id, before.copy(liked = intent, likeDelta = deltaFor(intent, serverComment.liked, before.likeDelta)))
                    intent
                } else {
                    putComment(serverComment.id, before)
                    toasts.show(r.userMessage())
                    null
                }
            }
            else -> {
                putComment(serverComment.id, before)
                toasts.show(r.userMessage())
                null
            }
        }
    }

    // ------------------------------------------------------------------ 生命周期

    /**
     * 退出登录 / 会话失效时清空。这些覆盖量属于上一个用户，必须丢弃。
     */
    fun clear() {
        _noteOverrides.value = emptyMap()
        _followOverrides.value = emptyMap()
        _commentOverrides.value = emptyMap()
    }

    /** 发布成功、删除笔记、或从收藏列表取消收藏后，丢弃单条笔记的覆盖。 */
    fun forget(postId: Long) {
        _noteOverrides.value = _noteOverrides.value - postId
    }

    /**
     * 收藏/点赞列表的成员变化会使「我的收藏/赞过」列表失效；
     * 递增该版本号，订阅方可据此触发刷新（避免轮询或全局重建）。
     */
    private val _collectionVersion = MutableStateFlow(0)
    val collectionVersion: StateFlow<Int> = _collectionVersion.asStateFlow()

    /** 在收藏成功/取消后调用，通知依赖收藏集合的页面刷新。 */
    fun invalidateCollections() {
        _collectionVersion.value += 1
    }

    // ------------------------------------------------------------------ 内部

    private fun putNote(id: Long, value: NoteInteraction) {
        _noteOverrides.value = _noteOverrides.value + (id to value)
    }

    private fun putFollow(id: Long, value: Boolean?) {
        _followOverrides.value = if (value == null) {
            // null = 回到「无覆盖」，用服务端值（回滚场景）
            _followOverrides.value - id
        } else {
            _followOverrides.value + (id to value)
        }
    }

    private fun putComment(id: Long, value: CommentInteraction) {
        _commentOverrides.value = _commentOverrides.value + (id to value)
    }

    /**
     * 相对服务端基准值重算增量：显示计数 = serverCount + delta。
     * [authoritative] 为服务端确认后的目标状态，[serverBase] 为服务端基准状态，
     * [previousDelta] 为本次操作前已有的增量（用于幂等分支保留历史增量）。
     */
    private fun deltaFor(authoritative: Boolean, serverBase: Boolean, previousDelta: Int): Int = when {
        authoritative == serverBase -> 0
        serverBase -> previousDelta - 1
        else -> previousDelta + 1
    }

    private companion object {
        /** 已关注/未关注/已操作过/未操作过 —— toggle 的幂等冲突，不视为错误。 */
        val IDEMPOTENT_CODES = setOf(6001, 6002, 7001, 7002)
    }
}
