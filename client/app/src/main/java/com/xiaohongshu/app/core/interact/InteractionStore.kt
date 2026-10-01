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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 某个笔记的本地互动覆盖量。
 *
 * [liked] / [collected] 为 null 表示「无覆盖，用服务端值」；
 * [likeDelta] / [collectDelta] 是相对**最后一次 toggle 所传快照**计数的净增量，
 * 该快照的状态位记录在 [likeBase] / [collectBase]。merge 时快照状态位**等于基准位**
 * 才叠 delta（旧快照）；等于最终态（toggle 后新拉的值，计数已含变化）则不叠。
 * 每次 toggle 都以传入快照**重新定基并重算净增量**（不累加）——各页缓存的快照
 * 基准不同，累加会让计数偏移（如卡片收藏 +1 后进详情取消，计数不回落）。
 */
data class NoteInteraction(
    val liked: Boolean? = null,
    val collected: Boolean? = null,
    val likeDelta: Int = 0,
    val collectDelta: Int = 0,
    val likeBase: Boolean? = null,
    val collectBase: Boolean? = null,
)

/** 评论点赞的本地覆盖（同 [NoteInteraction] 的思路，只是没有收藏）。 */
data class CommentInteraction(
    val liked: Boolean? = null,
    val likeDelta: Int = 0,
    val likeBase: Boolean? = null,
)

/**
 * 全局互动状态中心（D1/D2 状态机的唯一实现）。
 *
 * 为什么需要它：同一个笔记会同时出现在首页卡片、搜索结果、个人主页、详情页；
 * 同一个作者会出现在详情作者栏与他人主页。若各页各自维护状态，从详情返回列表后
 * 赞数会「跳回去」。这里用一份 overrides 让所有页面读同一份真相。
 *
 * 语义（D1/D2）：
 * - **乐观更新**：点击立即改 UI，再发请求；失败**回滚**并可能伴随全局错误 Toast（I2）；
 * - **幂等容错**：toggle 接口的 6001/6002/7001/7002 视为「已达目标状态」，不弹错；
 * - **指令序号**：同一目标的每次 toggle 递增序号（笔记的赞/藏各一、作者、评论）。
 *   响应到达时若已有更新的 toggle 发出，陈旧响应**整体丢弃**——不写状态、不回滚、
 *   不 Toast，避免旧响应覆盖新意图、迟到错误提示、以及回滚连坐抹掉其他字段的写入；
 *   回滚也只还原**本次动过的字段**，保留期间其他 toggle 写下的字段；
 * - 游客点击不进入本类——由调用方先做登录拦截（A2）。
 */
class InteractionStore(
    private val likeApi: LikeApi,
    private val collectApi: CollectApi,
    private val followApi: FollowApi,
    private val toasts: ToastController,
) {

    /**
     * 在途 toggle 的执行域。**不得借用页面 ViewModel 的 scope**：退页销毁会取消协程，
     * 乐观覆盖量就成了孤儿——不回滚、无错误 Toast，且后续反向 toggle 会与服务端真实
     * 状态倒置。Main.immediate 让点击线程上乐观态同步生效（原 viewModelScope 同款行为）。
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _noteOverrides = MutableStateFlow<Map<Long, NoteInteraction>>(emptyMap())
    val noteOverrides: StateFlow<Map<Long, NoteInteraction>> = _noteOverrides.asStateFlow()

    private val _followOverrides = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    val followOverrides: StateFlow<Map<Long, Boolean>> = _followOverrides.asStateFlow()

    private val _commentOverrides = MutableStateFlow<Map<Long, CommentInteraction>>(emptyMap())
    val commentOverrides: StateFlow<Map<Long, CommentInteraction>> = _commentOverrides.asStateFlow()

    // 指令序号（单线程 Main 访问，无需加锁）：赞/藏各一，防止同字段的陈旧响应覆盖新意图
    private val likeEpochs = mutableMapOf<Long, Long>()
    private val collectEpochs = mutableMapOf<Long, Long>()
    private val followEpochs = mutableMapOf<Long, Long>()
    private val commentEpochs = mutableMapOf<Long, Long>()

    // ------------------------------------------------------------------ 读取合并

    /**
     * 把本地乐观覆盖合并进服务端返回的笔记。UI 一律渲染本函数的返回值。
     *
     * 增量只叠在**基准相同的旧快照**上：快照状态位等于覆盖记录的基准位（likeBase/
     * collectBase）→ 是 toggle 前的旧快照，补 delta；等于最终态 → toggle 后新拉的
     * 值（如进详情页），计数已含本次变化，直接用服务端值。
     */
    fun merge(note: Note): Note {
        val ov = _noteOverrides.value[note.id] ?: return note
        val liked = ov.liked ?: note.liked
        val collected = ov.collected ?: note.collected
        return note.copy(
            liked = liked,
            collected = collected,
            likeCount = (note.likeCount + if (note.liked == ov.likeBase) ov.likeDelta else 0).coerceAtLeast(0),
            collectCount = (note.collectCount + if (note.collected == ov.collectBase) ov.collectDelta else 0).coerceAtLeast(0),
        )
    }

    fun mergeAll(notes: List<Note>): List<Note> = if (_noteOverrides.value.isEmpty()) notes else notes.map(::merge)

    /** 作者关注态合并（详情作者栏 / 他人主页）。 */
    fun followedOf(authorId: Long, serverValue: Boolean): Boolean =
        _followOverrides.value[authorId] ?: serverValue

    /** 评论点赞态合并（基准位判定同 [merge]）。 */
    fun merge(comment: Comment): Comment {
        val ov = _commentOverrides.value[comment.id] ?: return comment
        val liked = ov.liked ?: comment.liked
        return comment.copy(
            liked = liked,
            likeCount = (comment.likeCount + if (comment.liked == ov.likeBase) ov.likeDelta else 0).coerceAtLeast(0),
        )
    }

    fun mergeComments(comments: List<Comment>): List<Comment> =
        if (_commentOverrides.value.isEmpty()) comments else comments.map(::merge)

    // ------------------------------------------------------------------ 点赞笔记

    /**
     * D1 点赞/取消（乐观 + 回滚）。在 [scope] 执行、不随页面销毁取消；结果经覆盖量流转，无返回值。
     *
     * **[serverNote] 必须是服务端原值，不能是 [merge] 的返回值。**
     * 本方法用 `serverNote.likeCount` 作为计数基准、`serverNote.liked` 作为状态基准来计算增量；
     * 若传入已合并过的值，合并后的 count 会被当成基准重复叠加，导致计数永久偏移。
     * 正确写法：渲染用 `merge(raw)`，变更用 `toggleLike(raw)`（见开发规范 §4.3）。
     */
    fun toggleLike(serverNote: Note) {
        scope.launch {
            val myEpoch = bumpEpoch(likeEpochs, serverNote.id)
            val before = _noteOverrides.value[serverNote.id] ?: NoteInteraction()
            val intent = !(before.liked ?: serverNote.liked)

            // 乐观：以传入快照定基并重算净增量（不累加）；基于 live 只动 like 三字段
            putNote(
                serverNote.id,
                noteNow(serverNote.id).copy(liked = intent, likeBase = serverNote.liked, likeDelta = deltaFor(intent, serverNote.liked)),
            )

            val r = apiCall { likeApi.togglePostLike(serverNote.id).unwrap() }
            if (!isLatest(likeEpochs, serverNote.id, myEpoch)) return@launch // 陈旧响应整体丢弃

            when (r) {
                is ApiResult.Ok -> {
                    val authoritative = r.data.liked
                    putNote(
                        serverNote.id,
                        noteNow(serverNote.id).copy(liked = authoritative, likeBase = serverNote.liked, likeDelta = deltaFor(authoritative, serverNote.liked)),
                    )
                    // 服务端返回的 message（"点赞成功"/"取消点赞成功"）不需要每次 Toast，保持原版静默体验
                }
                is ApiResult.Biz -> {
                    if (r.code in IDEMPOTENT_CODES) {
                        putNote(
                            serverNote.id,
                            noteNow(serverNote.id).copy(liked = intent, likeBase = serverNote.liked, likeDelta = deltaFor(intent, serverNote.liked)),
                        )
                    } else {
                        rollbackLike(serverNote.id, before)
                        toasts.show(r.userMessage())
                    }
                }
                else -> {
                    rollbackLike(serverNote.id, before)
                    toasts.show(r.userMessage())
                }
            }
        }
    }

    // ------------------------------------------------------------------ 收藏笔记

    /**
     * D1 收藏/取消。收藏同时影响「我页-收藏」Tab 内容，成功后经 [invalidateCollections]
     * 通知刷新。**[serverNote] 传服务端原值**，理由同 [toggleLike]。在 [scope] 执行。
     */
    fun toggleCollect(serverNote: Note) {
        scope.launch {
            val myEpoch = bumpEpoch(collectEpochs, serverNote.id)
            val before = _noteOverrides.value[serverNote.id] ?: NoteInteraction()
            val intent = !(before.collected ?: serverNote.collected)

            putNote(
                serverNote.id,
                noteNow(serverNote.id).copy(collected = intent, collectBase = serverNote.collected, collectDelta = deltaFor(intent, serverNote.collected)),
            )

            val r = apiCall { collectApi.toggleCollect(serverNote.id).unwrap() }
            if (!isLatest(collectEpochs, serverNote.id, myEpoch)) return@launch // 陈旧响应整体丢弃

            when (r) {
                is ApiResult.Ok -> {
                    val authoritative = r.data.collected
                    putNote(
                        serverNote.id,
                        noteNow(serverNote.id).copy(
                            collected = authoritative,
                            collectBase = serverNote.collected,
                            collectDelta = deltaFor(authoritative, serverNote.collected),
                        ),
                    )
                    invalidateCollections()
                }
                is ApiResult.Biz -> {
                    if (r.code in IDEMPOTENT_CODES) {
                        putNote(
                            serverNote.id,
                            noteNow(serverNote.id).copy(collected = intent, collectBase = serverNote.collected, collectDelta = deltaFor(intent, serverNote.collected)),
                        )
                        invalidateCollections()
                    } else {
                        rollbackCollect(serverNote.id, before)
                        toasts.show(r.userMessage())
                    }
                }
                else -> {
                    rollbackCollect(serverNote.id, before)
                    toasts.show(r.userMessage())
                }
            }
        }
    }

    // ------------------------------------------------------------------ 关注作者

    /**
     * D2 关注/取关。详情作者栏（描边胶囊）与他人主页（通栏大按钮）共用同一状态机。
     * 自己的笔记/主页不显示关注按钮，调用方负责不调用。在 [scope] 执行。
     */
    fun toggleFollow(authorId: Long, serverValue: Boolean) {
        scope.launch {
            val myEpoch = bumpEpoch(followEpochs, authorId)
            val before = _followOverrides.value[authorId]
            val intent = !(before ?: serverValue)
            putFollow(authorId, intent)

            val r = apiCall { followApi.toggleFollow(authorId).unwrap() }
            if (!isLatest(followEpochs, authorId, myEpoch)) return@launch // 陈旧响应整体丢弃

            when (r) {
                is ApiResult.Ok -> putFollow(authorId, r.data.followed)
                is ApiResult.Biz -> {
                    if (r.code in IDEMPOTENT_CODES || r.code == Code.CANNOT_FOLLOW_SELF) {
                        if (r.code == Code.CANNOT_FOLLOW_SELF) {
                            putFollow(authorId, before)
                            toasts.show("不能关注自己")
                        } else {
                            putFollow(authorId, intent)
                        }
                    } else {
                        putFollow(authorId, before)
                        toasts.show(r.userMessage())
                    }
                }
                else -> {
                    putFollow(authorId, before)
                    toasts.show(r.userMessage())
                }
            }
        }
    }

    // ------------------------------------------------------------------ 点赞评论

    /**
     * 图文页流评论 ♥ / 视频评论面板内评论 ♥ 共用。
     * **[serverComment] 传服务端原值**，理由同 [toggleLike]。在 [scope] 执行。
     */
    fun toggleCommentLike(serverComment: Comment) {
        scope.launch {
            val myEpoch = bumpEpoch(commentEpochs, serverComment.id)
            val before = _commentOverrides.value[serverComment.id] ?: CommentInteraction()
            val intent = !(before.liked ?: serverComment.liked)

            putComment(
                serverComment.id,
                commentNow(serverComment.id).copy(liked = intent, likeBase = serverComment.liked, likeDelta = deltaFor(intent, serverComment.liked)),
            )

            val r = apiCall { likeApi.toggleCommentLike(serverComment.id).unwrap() }
            if (!isLatest(commentEpochs, serverComment.id, myEpoch)) return@launch // 陈旧响应整体丢弃

            when (r) {
                is ApiResult.Ok -> {
                    val authoritative = r.data.liked
                    putComment(
                        serverComment.id,
                        commentNow(serverComment.id).copy(liked = authoritative, likeBase = serverComment.liked, likeDelta = deltaFor(authoritative, serverComment.liked)),
                    )
                }
                is ApiResult.Biz -> {
                    if (r.code in IDEMPOTENT_CODES) {
                        putComment(
                            serverComment.id,
                            commentNow(serverComment.id).copy(liked = intent, likeBase = serverComment.liked, likeDelta = deltaFor(intent, serverComment.liked)),
                        )
                    } else {
                        putComment(serverComment.id, before)
                        toasts.show(r.userMessage())
                    }
                }
                else -> {
                    putComment(serverComment.id, before)
                    toasts.show(r.userMessage())
                }
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
        likeEpochs.clear()
        collectEpochs.clear()
        followEpochs.clear()
        commentEpochs.clear()
    }

    /** 发布成功、删除笔记、或从收藏列表取消收藏后，丢弃单条笔记的覆盖。 */
    fun forget(postId: Long) {
        _noteOverrides.value = _noteOverrides.value - postId
        likeEpochs.remove(postId)
        collectEpochs.remove(postId)
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

    private fun noteNow(id: Long): NoteInteraction = _noteOverrides.value[id] ?: NoteInteraction()

    private fun commentNow(id: Long): CommentInteraction = _commentOverrides.value[id] ?: CommentInteraction()

    /** 只还原 like 三字段到 [before] 时的值，保留期间其他 toggle（收藏）写下的字段。 */
    private fun rollbackLike(id: Long, before: NoteInteraction) = rollbackNote(id) {
        it.copy(liked = before.liked, likeBase = before.likeBase, likeDelta = before.likeDelta)
    }

    /** 只还原 collect 三字段到 [before] 时的值，保留期间其他 toggle（点赞）写下的字段。 */
    private fun rollbackCollect(id: Long, before: NoteInteraction) = rollbackNote(id) {
        it.copy(collected = before.collected, collectBase = before.collectBase, collectDelta = before.collectDelta)
    }

    /** 字段级回滚的落盘：还原后全空则删键，避免残留无意义覆盖。 */
    private fun rollbackNote(id: Long, transform: (NoteInteraction) -> NoteInteraction) {
        val reverted = transform(_noteOverrides.value[id] ?: NoteInteraction())
        if (reverted == NoteInteraction()) {
            _noteOverrides.value = _noteOverrides.value - id
        } else {
            _noteOverrides.value = _noteOverrides.value + (id to reverted)
        }
    }

    /** 发出一次 toggle：序号 +1 并返回本次序号（Main 单线程访问，无需加锁）。 */
    private fun bumpEpoch(epochMap: MutableMap<Long, Long>, id: Long): Long {
        val next = (epochMap[id] ?: 0L) + 1L
        epochMap[id] = next
        return next
    }

    /** 响应仍属于该字段最新一次 toggle（期间无更新的 toggle 发出）。 */
    private fun isLatest(epochMap: MutableMap<Long, Long>, id: Long, epoch: Long): Boolean = epochMap[id] == epoch

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
     * 相对 [serverBase] 快照计数的**净增量**：目标态与基准态相同 → 0；否则按翻转方向 ±1。
     * 每次 toggle 都以传入快照重新定基、重算（不累加）——各页缓存的快照基准不同，
     * 累加会让计数偏移（如卡片收藏 +1 后进详情取消，计数不回落）。
     */
    private fun deltaFor(target: Boolean, serverBase: Boolean): Int =
        if (target == serverBase) 0 else if (serverBase) -1 else 1

    private companion object {
        /** 已关注/未关注/已操作过/未操作过 —— toggle 的幂等冲突，不视为错误。 */
        val IDEMPOTENT_CODES = setOf(6001, 6002, 7001, 7002)
    }
}
