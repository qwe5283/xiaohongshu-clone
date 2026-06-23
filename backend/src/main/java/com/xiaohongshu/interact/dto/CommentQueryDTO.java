package com.xiaohongshu.interact.dto;

import com.xiaohongshu.common.result.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评论查询DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CommentQueryDTO extends PageRequest {

    /**
     * 笔记ID
     */
    private Long postId;

    /**
     * 父评论ID（查询回复时使用）
     */
    private Long parentId;
}
