package com.xiaohongshu.notification.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaohongshu.common.result.PageRequest;
import com.xiaohongshu.interact.entity.Comment;
import com.xiaohongshu.notification.entity.Notification;
import com.xiaohongshu.notification.mapper.NotificationMapper;
import com.xiaohongshu.notification.service.NotificationService;
import com.xiaohongshu.notification.vo.NotificationVO;
import com.xiaohongshu.post.entity.Post;
import com.xiaohongshu.post.service.PostService;
import com.xiaohongshu.user.entity.User;
import com.xiaohongshu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 消息通知服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification> implements NotificationService {

    private final UserService userService;
    private final PostService postService;

    @Override
    public void createPostLikeNotification(Long senderId, Post post) {
        if (post == null) {
            return;
        }
        createNotification(post.getUserId(), senderId, TYPE_LIKE_POST, post.getId(), 0L, "");
    }

    @Override
    public void createPostCollectNotification(Long senderId, Post post) {
        if (post == null) {
            return;
        }
        createNotification(post.getUserId(), senderId, TYPE_COLLECT_POST, post.getId(), 0L, "");
    }

    @Override
    public void createPostCommentNotification(Long senderId, Post post, Comment comment) {
        if (post == null || comment == null) {
            return;
        }
        createNotification(post.getUserId(), senderId, TYPE_COMMENT_POST, post.getId(), comment.getId(), comment.getContent());
    }

    @Override
    public void createCommentReplyNotification(Long senderId, Comment reply) {
        if (reply == null || reply.getReplyUserId() == null || reply.getReplyUserId() <= 0) {
            return;
        }
        createNotification(reply.getReplyUserId(), senderId, TYPE_REPLY_COMMENT,
                reply.getPostId(), reply.getId(), reply.getContent());
    }

    @Override
    public void createCommentLikeNotification(Long senderId, Comment comment) {
        if (comment == null) {
            return;
        }
        createNotification(comment.getUserId(), senderId, TYPE_LIKE_COMMENT,
                comment.getPostId(), comment.getId(), comment.getContent());
    }

    @Override
    public void createFollowNotification(Long senderId, Long receiverId) {
        createNotification(receiverId, senderId, TYPE_FOLLOW_USER, 0L, 0L, "");
    }

    @Override
    public long getUnreadCount(Long receiverId) {
        return count(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getReceiverId, receiverId)
                .eq(Notification::getIsRead, 0));
    }

    @Override
    public IPage<NotificationVO> getNotificationPage(Long receiverId, PageRequest queryDTO, Integer type) {
        Page<Notification> page = new Page<>(queryDTO.getPageNumSafe(), queryDTO.getPageSizeSafe());
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getReceiverId, receiverId)
                .orderByDesc(Notification::getCreateTime);
        if (type != null) {
            wrapper.eq(Notification::getType, type);
        }
        return page(page, wrapper).convert(this::convertToVO);
    }

    @Override
    public void markAllAsRead(Long receiverId) {
        update(new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getReceiverId, receiverId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1)
                .set(Notification::getReadTime, LocalDateTime.now()));
    }

    private void createNotification(Long receiverId, Long senderId, Integer type,
                                    Long postId, Long commentId, String content) {
        if (receiverId == null || senderId == null || receiverId.equals(senderId)) {
            return;
        }

        Notification notification = new Notification();
        notification.setReceiverId(receiverId);
        notification.setSenderId(senderId);
        notification.setType(type);
        notification.setPostId(postId != null ? postId : 0L);
        notification.setCommentId(commentId != null ? commentId : 0L);
        notification.setContent(content != null ? content : "");
        notification.setIsRead(0);
        save(notification);
        log.info("消息通知创建成功，接收者ID：{}，触发者ID：{}，类型：{}", receiverId, senderId, type);
    }

    private NotificationVO convertToVO(Notification notification) {
        NotificationVO vo = new NotificationVO();
        BeanUtil.copyProperties(notification, vo);
        vo.setRead(notification.getIsRead() != null && notification.getIsRead() == 1);
        vo.setTypeText(getTypeText(notification.getType()));

        try {
            User sender = userService.getById(notification.getSenderId());
            if (sender != null) {
                vo.setSenderNickname(sender.getNickname());
                vo.setSenderAvatar(sender.getAvatar());
            }
        } catch (Exception e) {
            log.warn("获取通知发送者信息失败：{}", e.getMessage());
        }

        if (notification.getPostId() != null && notification.getPostId() > 0) {
            try {
                Post post = postService.getById(notification.getPostId());
                if (post != null) {
                    vo.setPostTitle(post.getTitle());
                    vo.setPostCoverImage(post.getCoverImage());
                }
            } catch (Exception e) {
                log.warn("获取通知关联笔记信息失败：{}", e.getMessage());
            }
        }

        return vo;
    }

    private String getTypeText(Integer type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case TYPE_LIKE_POST -> "赞了你的笔记";
            case TYPE_COLLECT_POST -> "收藏了你的笔记";
            case TYPE_COMMENT_POST -> "评论了你的笔记";
            case TYPE_REPLY_COMMENT -> "回复了你的评论";
            case TYPE_LIKE_COMMENT -> "赞了你的评论";
            case TYPE_FOLLOW_USER -> "开始关注你了";
            default -> "";
        };
    }
}
