package com.xiaohongshu.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 创建笔记请求DTO
 */
@Data
public class PostCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 标题
     */
    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题长度不能超过200个字符")
    private String title;

    /**
     * 正文内容
     */
    @Size(max = 10000, message = "正文内容长度不能超过10000个字符")
    private String content;

    /**
     * 视频URL（可选，最多1个）
     */
    private String videoUrl;

    /**
     * 图片URL列表（可选，最多9张）
     */
    @Size(max = 9, message = "图片数量不能超过9张")
    private List<String> imageUrls;
}
