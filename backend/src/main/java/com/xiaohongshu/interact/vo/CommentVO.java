package com.xiaohongshu.interact.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评论信息VO（返回给前端）
 */
@Data
public class CommentVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 评论ID
     */
    private Long id;

    /**
     * 笔记ID
     */
    private Long postId;

    /**
     * 评论用户ID
     */
    private Long userId;

    /**
     * 评论用户昵称
     */
    private String userNickname;

    /**
     * 评论用户头像
     */
    private String userAvatar;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 父评论ID，0表示一级评论
     */
    private Long parentId;

    /**
     * 回复的用户ID
     */
    private Long replyUserId;

    /**
     * 回复的用户昵称
     */
    private String replyUserNickname;

    /**
     * 点赞数
     */
    private Integer likeCount;

    /**
     * 回复数量（仅一级评论有值）
     */
    private Integer replyCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
