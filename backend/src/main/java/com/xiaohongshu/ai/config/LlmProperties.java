package com.xiaohongshu.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 大语言模型推理接口配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    /**
     * OpenAI-compatible API base URL，例如 https://api.openai.com/v1。
     */
    private String baseUrl;

    /**
     * 推理接口 API Key。
     */
    private String apiKey;

    /**
     * 模型名称。
     */
    private String model;

    /**
     * 请求超时时间，单位秒。
     */
    private Integer timeoutSeconds = 60;

    /**
     * 最大输出 token 数。
     */
    private Integer maxTokens = 1024;

    /**
     * 采样温度。
     */
    private Double temperature = 0.7;
}
