package com.xiaohongshu.interact.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 创建评论请求DTO
 */
@Data
@Schema(description = "创建评论请求")
public class CommentCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 笔记ID
     */
    @NotNull(message = "笔记ID不能为空")
    @Schema(description = "笔记ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long postId;

    /**
     * 评论内容
     */
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 500, message = "评论内容长度不能超过500个字符")
    @Schema(description = "评论内容（最多500字）", example = "写得真好，学到了！", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    /**
     * 父评论ID，0或null表示一级评论
     */
    @Schema(description = "父评论ID（0或null表示一级评论）", example = "0")
    private Long parentId = 0L;

    /**
     * 回复的用户ID
     */
    @Schema(description = "回复的用户ID（回复评论时使用）", example = "2")
    private Long replyUserId = 0L;
}
