package com.xiaohongshu.post.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "创建笔记请求")
public class PostCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 标题
     */
    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题长度不能超过200个字符")
    @Schema(description = "笔记标题（最多200字）", example = "我的第一篇文章", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    /**
     * 正文内容
     */
    @Size(max = 10000, message = "正文内容长度不能超过10000个字符")
    @Schema(description = "正文内容（最多10000字）", example = "这是一篇分享笔记...")
    private String content;

    /**
     * 视频URL（可选，最多1个）
     */
    @Schema(description = "视频URL（可选）", example = "https://example.com/video.mp4")
    private String videoUrl;

    /**
     * 图片URL列表（可选，最多9张）
     */
    @Size(max = 9, message = "图片数量不能超过9张")
    @Schema(description = "图片URL列表（可选，最多9张）", example = "[\"https://example.com/img1.jpg\", \"https://example.com/img2.jpg\"]")
    private List<String> imageUrls;

    /**
     * 封面图URL（可选，显式指定时优先于自动推导。视频笔记无图片时可传入视频首帧）
     */
    @Schema(description = "封面图URL（可选，不传则自动推导）", example = "https://example.com/cover.jpg")
    private String coverImage;
}
