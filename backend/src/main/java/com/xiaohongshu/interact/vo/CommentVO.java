package com.xiaohongshu.interact.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评论信息VO（返回给前端）
 */
@Data
@Schema(description = "评论信息")
public class CommentVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "评论ID", example = "1")
    private Long id;

    @Schema(description = "笔记ID", example = "1")
    private Long postId;

    @Schema(description = "评论用户ID", example = "1")
    private Long userId;

    @Schema(description = "评论用户昵称", example = "张三")
    private String userNickname;

    @Schema(description = "评论用户头像", example = "https://example.com/avatar.jpg")
    private String userAvatar;

    @Schema(description = "评论内容", example = "写得真好，学到了！")
    private String content;

    @Schema(description = "父评论ID，0表示一级评论", example = "0")
    private Long parentId;

    @Schema(description = "回复的用户ID", example = "2")
    private Long replyUserId;

    @Schema(description = "回复的用户昵称", example = "李四")
    private String replyUserNickname;

    @Schema(description = "点赞数", example = "5")
    private Integer likeCount;

    @Schema(description = "当前登录用户是否已点赞", example = "false")
    private Boolean liked;

    @Schema(description = "回复数量（仅一级评论有值）", example = "3")
    private Integer replyCount;

    @Schema(description = "创建时间", example = "2024-01-01 12:00:00")
    private LocalDateTime createTime;
}
