package com.xiaohongshu.interact.dto;

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
public class CommentCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 笔记ID
     */
    @NotNull(message = "笔记ID不能为空")
    private Long postId;

    /**
     * 评论内容
     */
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 500, message = "评论内容长度不能超过500个字符")
    private String content;

    /**
     * 父评论ID，0或null表示一级评论
     */
    private Long parentId = 0L;

    /**
     * 回复的用户ID
     */
    private Long replyUserId = 0L;
}
