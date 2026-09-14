package com.xiaohongshu.app.feature.publish

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohongshu.app.core.net.ApiResult
import com.xiaohongshu.app.core.publish.DraftMedia
import com.xiaohongshu.app.core.publish.PublishDraft
import com.xiaohongshu.app.data.repo.PostRepository
import kotlinx.coroutines.launch

/**
 * E3 写文字页 ViewModel。
 *
 * 「下一步」调 `POST /api/post/text-image`（契约 §2.11）拿**可直接入库的 URL**——
 * 因此 E3 无需二次上传（§8 的上传只服务 E1/E2 的本地素材）。成功后把配图作为**唯一图片**
 * 并入草稿、所写文字预填标题，再推入 E2。
 *
 * 失败**不进入 E2**（不允许无素材笔记）：Toast 后停留本页可重试。
 */
class WriteTextViewModel(
    private val draft: PublishDraft,
    private val posts: PostRepository,
) : ViewModel() {

    var text by mutableStateOf("")
        private set

    /** E3「下一步」的生成中态（按钮显示「生成中...」并禁用）。 */
    var generating by mutableStateOf(false)
        private set

    /** 有输入才点亮「下一步」，生成中不可再点。 */
    val canGenerate: Boolean get() = text.isNotBlank() && !generating

    fun onTextChange(value: String) {
        if (generating) return
        text = value
    }

    /**
     * 输入的长度**不做客户端截断**：
     * ① 配图接口的 ≤100 字由后端截断（契约 §2.11「建议 ≤100 字，超出后端截断」）；
     * ② 预填标题超 200 必须可达，否则 E4 的「标题不能超过200个字符」永远触发不到（§7 E3）。
     */
    fun generate(onSuccess: () -> Unit, onFailure: () -> Unit) {
        if (!canGenerate) return
        val input = text.trim()
        generating = true
        viewModelScope.launch {
            when (val result = posts.generateTextImage(input)) {
                is ApiResult.Ok -> {
                    // 成功：重置草稿 → 单张配图入媒体列表（计入「图片 ≤9」，可在 E2 删除）
                    draft.reset()
                    draft.addMedia(
                        listOf(
                            DraftMedia(
                                localPath = "",
                                isVideo = false,
                                mimeType = MIME_TEXT_IMAGE,
                                previewPath = "",
                                remoteUrl = result.data,
                            ),
                        ),
                    )
                    draft.generatedImageUrl = result.data
                    draft.prefilledTitle = input
                    generating = false
                    onSuccess()
                }

                else -> {
                    generating = false
                    onFailure()
                }
            }
        }
    }
}

private const val MIME_TEXT_IMAGE = "image/png"
