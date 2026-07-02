package com.xiaohongshu.ai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.xiaohongshu.ai.config.LlmProperties;
import com.xiaohongshu.common.exception.BusinessException;
import com.xiaohongshu.common.result.ResultCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI-compatible /chat/completions 客户端。
 */
@Slf4j
@Component
public class OpenAiCompatibleClient implements LlmClient {

    private static final String DEFAULT_SYSTEM_PROMPT =
            "你是小红书复刻项目中的AI助手，回答要简洁、友好、实用。";

    private final LlmProperties properties;

    public OpenAiCompatibleClient(LlmProperties properties) {
        this.properties = properties;
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        validateConfig();

        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", StringUtils.hasText(systemPrompt) ? systemPrompt : DEFAULT_SYSTEM_PROMPT));
        messages.add(new Message("user", userMessage));

        ChatCompletionRequest request = new ChatCompletionRequest(
                properties.getModel(),
                messages,
                properties.getTemperature(),
                properties.getMaxTokens()
        );

        try {
            ChatCompletionResponse response = restClient()
                    .post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .body(request)
                    .retrieve()
                    .body(ChatCompletionResponse.class);

            String answer = extractAnswer(response);
            if (!StringUtils.hasText(answer)) {
                throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "AI服务未返回有效内容");
            }
            return answer.trim();
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("调用AI服务失败：{}", e.getMessage());
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "AI服务暂时不可用，请稍后重试");
        }
    }

    private RestClient restClient() {
        int timeoutSeconds = properties.getTimeoutSeconds() == null ? 60 : properties.getTimeoutSeconds();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        return RestClient.builder()
                .baseUrl(normalizeBaseUrl(properties.getBaseUrl()))
                .requestFactory(requestFactory)
                .build();
    }

    private void validateConfig() {
        if (!StringUtils.hasText(properties.getBaseUrl())
                || !StringUtils.hasText(properties.getApiKey())
                || !StringUtils.hasText(properties.getModel())) {
            throw new BusinessException(ResultCode.AI_CONFIG_ERROR, "AI服务未配置，请联系管理员");
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String extractAnswer(ChatCompletionResponse response) {
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            return null;
        }
        Choice choice = response.getChoices().get(0);
        return choice == null || choice.getMessage() == null ? null : choice.getMessage().getContent();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ChatCompletionRequest {
        private String model;
        private List<Message> messages;
        private Double temperature;
        private Integer max_tokens;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Message {
        private String role;
        private String content;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ChatCompletionResponse {
        private List<Choice> choices;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Choice {
        private Message message;
    }
}
