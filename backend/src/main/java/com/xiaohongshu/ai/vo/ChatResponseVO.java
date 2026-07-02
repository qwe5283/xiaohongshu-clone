package com.xiaohongshu.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI对话响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI对话响应")
public class ChatResponseVO {

    @Schema(description = "AI回答内容")
    private String answer;
}
