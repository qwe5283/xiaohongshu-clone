package com.xiaohongshu.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 文本配图请求DTO
 */
@Data
public class TextImageDTO {

    /**
     * 文本内容，最多20字
     */
    @NotBlank(message = "文本内容不能为空")
    @Size(max = 20, message = "文本内容不能超过20字")
    private String text;
}
