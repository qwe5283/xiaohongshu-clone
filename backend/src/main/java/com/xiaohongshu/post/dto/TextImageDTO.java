package com.xiaohongshu.post.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 文本配图请求DTO
 */
@Data
@Schema(description = "文本配图请求")
public class TextImageDTO {

    /**
     * 文本内容，最多20字
     */
    @NotBlank(message = "文本内容不能为空")
    @Size(max = 20, message = "文本内容不能超过20字")
    @Schema(description = "文本内容（最多20字）", example = "你好世界", requiredMode = Schema.RequiredMode.REQUIRED)
    private String text;
}
