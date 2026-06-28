package com.xiaohongshu.post.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 笔记图片VO
 */
@Data
@Schema(description = "笔记图片信息")
public class PostImageVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "图片ID", example = "1")
    private Long id;

    @Schema(description = "图片URL", example = "https://example.com/img1.jpg")
    private String imageUrl;

    @Schema(description = "排序顺序", example = "1")
    private Integer sortOrder;

    @Schema(description = "图片宽度（像素）", example = "1080")
    private Integer width;

    @Schema(description = "图片高度（像素）", example = "720")
    private Integer height;
}
