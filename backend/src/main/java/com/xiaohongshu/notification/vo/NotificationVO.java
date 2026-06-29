package com.xiaohongshu.notification.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息通知展示对象
 */
@Data
public class NotificationVO {

    private Long id;

    private Long receiverId;

    private Long senderId;

    private String senderNickname;

    private String senderAvatar;

    private Integer type;

    private String typeText;

    private Long postId;

    private String postTitle;

    private String postCoverImage;

    private Long commentId;

    private String content;

    private Boolean read;

    private LocalDateTime createTime;
}
