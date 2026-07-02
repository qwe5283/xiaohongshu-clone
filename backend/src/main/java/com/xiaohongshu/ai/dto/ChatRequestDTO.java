package com.xiaohongshu.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI对话请求。
 */
@Data
public class ChatRequestDTO {

    @NotBlank(message = "问题不能为空")
    @Size(max = 4000, message = "问题不能超过4000个字符")
    @Schema(description = "问题内容（最多4000字）", example = "帮我规划一套每周3次的居家有氧运动方案", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;

    @Size(max = 1000, message = "系统提示词不能超过1000个字符")
    @Schema(description = "系统提示词（最多1000字）", example = "You're a helpful assistant.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String systemPrompt;
}
