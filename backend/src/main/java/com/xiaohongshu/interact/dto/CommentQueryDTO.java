package com.xiaohongshu.interact.dto;

import com.xiaohongshu.common.result.PageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评论查询DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "评论查询条件")
public class CommentQueryDTO extends PageRequest {

    /**
     * 笔记ID
     */
    @Schema(description = "笔记ID", example = "1")
    private Long postId;

    /**
     * 父评论ID（查询回复时使用）
     */
    @Schema(description = "父评论ID（查询回复时使用）", example = "1")
    private Long parentId;
}
