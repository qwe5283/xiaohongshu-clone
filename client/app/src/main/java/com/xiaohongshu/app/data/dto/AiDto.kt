package com.xiaohongshu.app.data.dto

import kotlinx.serialization.Serializable

/**
 * `POST /api/ai/chat` 请求体 —— 单轮问答，每次只发当前问题，不携带历史上下文（契约 §10.1）。
 */
@Serializable
data class ChatRequest(
    val message: String,
    val systemPrompt: String = "",
)

/**
 * `POST /api/ai/chat` 响应。
 *
 * [notes] 为 H2 笔记卡数据源（164×236），后端不返回笔记时为空数组，客户端只渲染文本气泡。
 */
@Serializable
data class ChatResponseDto(
    val answer: String = "",
    val notes: List<PostDto> = emptyList(),
)
