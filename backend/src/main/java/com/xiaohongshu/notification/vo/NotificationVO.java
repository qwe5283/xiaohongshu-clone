package com.xiaohongshu.notification.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息通知展示对象
 */
@Data
@Schema(description = "消息通知展示对象")
public class NotificationVO {

    @Schema(description = "通知ID")
    private Long id;

    @Schema(description = "接收者ID")
    private Long receiverId;

    @Schema(description = "发送者ID")
    private Long senderId;

    @Schema(description = "发送者昵称")
    private String senderNickname;

    @Schema(description = "发送者头像URL")
    private String senderAvatar;

    @Schema(description = "通知类型（0-点赞 1-评论 2-关注）")
    private Integer type;

    @Schema(description = "通知类型文本描述")
    private String typeText;

    @Schema(description = "关联笔记ID")
    private Long postId;

    @Schema(description = "关联笔记标题")
    private String postTitle;

    @Schema(description = "关联笔记封面图URL")
    private String postCoverImage;

    @Schema(description = "关联评论ID")
    private Long commentId;

    @Schema(description = "通知内容")
    private String content;

    @Schema(description = "是否已读")
    private Boolean read;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
