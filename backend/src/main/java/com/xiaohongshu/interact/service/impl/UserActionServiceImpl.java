package com.xiaohongshu.interact.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaohongshu.common.exception.BusinessException;
import com.xiaohongshu.common.result.ResultCode;
import com.xiaohongshu.interact.entity.UserAction;
import com.xiaohongshu.interact.mapper.UserActionMapper;
import com.xiaohongshu.interact.service.UserActionService;
import com.xiaohongshu.notification.service.NotificationService;
import com.xiaohongshu.post.entity.Post;
import com.xiaohongshu.post.service.PostService;
import com.xiaohongshu.interact.entity.Comment;
import com.xiaohongshu.interact.service.CommentService;
import com.xiaohongshu.user.entity.User;
import com.xiaohongshu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户行为服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserActionServiceImpl extends ServiceImpl<UserActionMapper, UserAction> implements UserActionService {

    private final PostService postService;
    private final CommentService commentService;
    private final UserService userService;
    private final NotificationService notificationService;

    /**
     * 目标类型：笔记
     */
    private static final int TARGET_TYPE_POST = 1;
    /**
     * 目标类型：评论
     */
    private static final int TARGET_TYPE_COMMENT = 2;
    /**
     * 行为类型：点赞
     */
    private static final int ACTION_TYPE_LIKE = 1;
    /**
     * 行为类型：收藏
     */
    private static final int ACTION_TYPE_COLLECT = 2;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleLikePost(Long userId, Long postId) {
        // 验证笔记是否存在
        Post post = postService.getById(postId);
        if (post == null) {
            throw new BusinessException(ResultCode.POST_NOT_FOUND);
        }

        Long postAuthorId = post.getUserId();

        return toggleAction(userId, postId, TARGET_TYPE_POST, ACTION_TYPE_LIKE,
                // 点赞数+1，同时更新笔记作者的获赞数
                () -> {
                    postService.update(new LambdaUpdateWrapper<Post>()
                            .eq(Post::getId, postId)
                            .setSql("like_count = like_count + 1"));
                    userService.update(new LambdaUpdateWrapper<User>()
                            .eq(User::getId, postAuthorId)
                            .setSql("liked_count = liked_count + 1"));
                    notificationService.createPostLikeNotification(userId, post);
                },
                // 点赞数-1，同时更新笔记作者的获赞数
                () -> {
                    postService.update(new LambdaUpdateWrapper<Post>()
                            .eq(Post::getId, postId)
                            .setSql("like_count = GREATEST(like_count - 1, 0)"));
                    userService.update(new LambdaUpdateWrapper<User>()
                            .eq(User::getId, postAuthorId)
                            .setSql("liked_count = GREATEST(liked_count - 1, 0)"));
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleLikeComment(Long userId, Long commentId) {
        // 验证评论是否存在
        Comment comment = commentService.getById(commentId);
        if (comment == null) {
            throw new BusinessException(ResultCode.COMMENT_NOT_FOUND);
        }

        return toggleAction(userId, commentId, TARGET_TYPE_COMMENT, ACTION_TYPE_LIKE,
                // 评论点赞数+1
                () -> {
                    commentService.update(new LambdaUpdateWrapper<Comment>()
                            .eq(Comment::getId, commentId)
                            .setSql("like_count = like_count + 1"));
                    notificationService.createCommentLikeNotification(userId, comment);
                },
                // 评论点赞数-1
                () -> commentService.update(new LambdaUpdateWrapper<Comment>()
                        .eq(Comment::getId, commentId)
                        .setSql("like_count = GREATEST(like_count - 1, 0)")));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleCollectPost(Long userId, Long postId) {
        // 验证笔记是否存在
        Post post = postService.getById(postId);
        if (post == null) {
            throw new BusinessException(ResultCode.POST_NOT_FOUND);
        }

        Long postAuthorId = post.getUserId();

        return toggleAction(userId, postId, TARGET_TYPE_POST, ACTION_TYPE_COLLECT,
                // 收藏数+1，同时更新笔记作者的获藏数
                () -> {
                    postService.update(new LambdaUpdateWrapper<Post>()
                            .eq(Post::getId, postId)
                            .setSql("collect_count = collect_count + 1"));
                    userService.update(new LambdaUpdateWrapper<User>()
                            .eq(User::getId, postAuthorId)
                            .setSql("collected_count = collected_count + 1"));
                    notificationService.createPostCollectNotification(userId, post);
                },
                // 收藏数-1，同时更新笔记作者的获藏数
                () -> {
                    postService.update(new LambdaUpdateWrapper<Post>()
                            .eq(Post::getId, postId)
                            .setSql("collect_count = GREATEST(collect_count - 1, 0)"));
                    userService.update(new LambdaUpdateWrapper<User>()
                            .eq(User::getId, postAuthorId)
                            .setSql("collected_count = GREATEST(collected_count - 1, 0)"));
                });
    }

    @Override
    public boolean isLikedPost(Long userId, Long postId) {
        return existsAction(userId, postId, TARGET_TYPE_POST, ACTION_TYPE_LIKE);
    }

    @Override
    public boolean isLikedComment(Long userId, Long commentId) {
        return existsAction(userId, commentId, TARGET_TYPE_COMMENT, ACTION_TYPE_LIKE);
    }

    @Override
    public boolean isCollectedPost(Long userId, Long postId) {
        return existsAction(userId, postId, TARGET_TYPE_POST, ACTION_TYPE_COLLECT);
    }

    @Override
    public List<Long> getLikedPostIds(Long userId, List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<UserAction> actions = list(new LambdaQueryWrapper<UserAction>()
                .eq(UserAction::getUserId, userId)
                .eq(UserAction::getTargetType, TARGET_TYPE_POST)
                .eq(UserAction::getActionType, ACTION_TYPE_LIKE)
                .in(UserAction::getTargetId, postIds));
        return actions.stream().map(UserAction::getTargetId).collect(Collectors.toList());
    }

    @Override
    public List<Long> getCollectedPostIds(Long userId, List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<UserAction> actions = list(new LambdaQueryWrapper<UserAction>()
                .eq(UserAction::getUserId, userId)
                .eq(UserAction::getTargetType, TARGET_TYPE_POST)
                .eq(UserAction::getActionType, ACTION_TYPE_COLLECT)
                .in(UserAction::getTargetId, postIds));
        return actions.stream().map(UserAction::getTargetId).collect(Collectors.toList());
    }

    @Override
    public IPage<Long> getCollectedPostIds(Long userId, int pageNum, int pageSize) {
        // 分页查询用户收藏的笔记ID，按收藏时间倒序
        Page<UserAction> page = new Page<>(pageNum, pageSize);
        IPage<UserAction> actionPage = page(page, new LambdaQueryWrapper<UserAction>()
                .eq(UserAction::getUserId, userId)
                .eq(UserAction::getTargetType, TARGET_TYPE_POST)
                .eq(UserAction::getActionType, ACTION_TYPE_COLLECT)
                .orderByDesc(UserAction::getCreateTime));

        // 转换为笔记ID的分页结果
        return actionPage.convert(UserAction::getTargetId);
    }

    @Override
    public IPage<Long> getLikedPostIds(Long userId, int pageNum, int pageSize) {
        // 分页查询用户点赞的笔记ID，按点赞时间倒序
        Page<UserAction> page = new Page<>(pageNum, pageSize);
        IPage<UserAction> actionPage = page(page, new LambdaQueryWrapper<UserAction>()
                .eq(UserAction::getUserId, userId)
                .eq(UserAction::getTargetType, TARGET_TYPE_POST)
                .eq(UserAction::getActionType, ACTION_TYPE_LIKE)
                .orderByDesc(UserAction::getCreateTime));

        // 转换为笔记ID的分页结果
        return actionPage.convert(UserAction::getTargetId);
    }

    /**
     * 切换行为（点赞/收藏）状态
     *
     * @return true-激活行为，false-取消行为
     */
    private boolean toggleAction(Long userId, Long targetId, int targetType, int actionType,
                                 Runnable onAdd, Runnable onRemove) {
        UserAction existing = getOne(new LambdaQueryWrapper<UserAction>()
                .eq(UserAction::getUserId, userId)
                .eq(UserAction::getTargetId, targetId)
                .eq(UserAction::getTargetType, targetType)
                .eq(UserAction::getActionType, actionType));

        if (existing != null) {
            // 已存在，取消行为
            boolean removed = removeById(existing.getId());
            if (removed) {
                onRemove.run();
                log.info("取消行为成功，用户ID：{}，目标ID：{}，目标类型：{}，行为类型：{}",
                        userId, targetId, targetType, actionType);
            } else {
                log.info("行为已被其他请求取消，用户ID：{}，目标ID：{}，目标类型：{}，行为类型：{}",
                        userId, targetId, targetType, actionType);
            }
            return false;
        } else {
            // 不存在，新增行为
            UserAction action = new UserAction();
            action.setUserId(userId);
            action.setTargetId(targetId);
            action.setTargetType(targetType);
            action.setActionType(actionType);
            try {
                save(action);
            } catch (DuplicateKeyException e) {
                log.info("行为已被其他请求创建，用户ID：{}，目标ID：{}，目标类型：{}，行为类型：{}",
                        userId, targetId, targetType, actionType);
                return true;
            }
            onAdd.run();
            log.info("恢复行为成功，用户ID：{}，目标ID：{}，目标类型：{}，行为类型：{}",
                    userId, targetId, targetType, actionType);
            return true;
        }

    }

    /**
     * 查询行为是否存在
     */
    private boolean existsAction(Long userId, Long targetId, int targetType, int actionType) {
        return count(new LambdaQueryWrapper<UserAction>()
                .eq(UserAction::getUserId, userId)
                .eq(UserAction::getTargetId, targetId)
                .eq(UserAction::getTargetType, targetType)
                .eq(UserAction::getActionType, actionType)) > 0;
    }
}
