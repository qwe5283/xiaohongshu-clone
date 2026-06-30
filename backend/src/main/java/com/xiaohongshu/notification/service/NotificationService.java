package com.xiaohongshu.notification.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaohongshu.common.result.PageRequest;
import com.xiaohongshu.interact.entity.Comment;
import com.xiaohongshu.notification.entity.Notification;
import com.xiaohongshu.notification.vo.NotificationVO;
import com.xiaohongshu.post.entity.Post;

/**
 * 消息通知服务
 */
public interface NotificationService extends IService<Notification> {

    int TYPE_LIKE_POST = 1;
    int TYPE_COLLECT_POST = 2;
    int TYPE_COMMENT_POST = 3;
    int TYPE_REPLY_COMMENT = 4;
    int TYPE_LIKE_COMMENT = 5;
    int TYPE_FOLLOW_USER = 6;

    void createPostLikeNotification(Long senderId, Post post);

    void createPostCollectNotification(Long senderId, Post post);

    void createPostCommentNotification(Long senderId, Post post, Comment comment);

    void createCommentReplyNotification(Long senderId, Comment reply);

    void createCommentLikeNotification(Long senderId, Comment comment);

    void createFollowNotification(Long senderId, Long receiverId);

    long getUnreadCount(Long receiverId);

    IPage<NotificationVO> getNotificationPage(Long receiverId, PageRequest queryDTO, Integer type);

    void markAllAsRead(Long receiverId);

    void markAsRead(Long notificationId, Long receiverId);
}
