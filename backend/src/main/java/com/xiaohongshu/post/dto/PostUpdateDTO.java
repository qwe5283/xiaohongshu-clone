package com.xiaohongshu.post.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 更新笔记请求DTO
 */
@Data
@Schema(description = "更新笔记请求")
public class PostUpdateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 笔记ID
     */
    @NotNull(message = "笔记ID不能为空")
    @Schema(description = "笔记ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    /**
     * 标题
     */
    @Size(max = 200, message = "标题长度不能超过200个字符")
    @Schema(description = "笔记标题（最多200字）", example = "修改后的标题")
    private String title;

    /**
     * 正文内容
     */
    @Size(max = 10000, message = "正文内容长度不能超过10000个字符")
    @Schema(description = "正文内容（最多10000字）", example = "更新后的内容...")
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
    @Schema(description = "图片URL列表（可选，最多9张）", example = "[\"https://example.com/img1.jpg\"]")
    private List<String> imageUrls;

    /**
     * 封面图URL（可选，显式指定时优先于自动推导）
     */
    @Schema(description = "封面图URL（可选）", example = "https://example.com/cover.jpg")
    private String coverImage;
}
