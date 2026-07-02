package com.xiaohongshu.ai.client;

/**
 * 大语言模型客户端。
 */
public interface LlmClient {

    String chat(String systemPrompt, String userMessage);
}
