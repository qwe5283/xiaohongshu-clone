package com.xiaohongshu.interact.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaohongshu.common.exception.BusinessException;
import com.xiaohongshu.common.result.ResultCode;
import com.xiaohongshu.interact.dto.CommentCreateDTO;
import com.xiaohongshu.interact.dto.CommentQueryDTO;
import com.xiaohongshu.interact.entity.Comment;
import com.xiaohongshu.interact.mapper.CommentMapper;
import com.xiaohongshu.interact.mapper.UserActionMapper;
import com.xiaohongshu.interact.service.CommentService;
import com.xiaohongshu.interact.vo.CommentVO;
import com.xiaohongshu.notification.service.NotificationService;
import com.xiaohongshu.post.entity.Post;
import com.xiaohongshu.post.service.PostService;
import com.xiaohongshu.user.entity.User;
import com.xiaohongshu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 评论服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    private final PostService postService;
    private final UserService userService;
    private final UserActionMapper userActionMapper;
    private final NotificationService notificationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentVO createComment(Long userId, CommentCreateDTO createDTO) {
        // 验证笔记是否存在
        Post post = postService.getById(createDTO.getPostId());
        if (post == null) {
            throw new BusinessException(ResultCode.POST_NOT_FOUND);
        }

        // 如果是回复，验证父评论是否存在且属于同一篇笔记
        if (createDTO.getParentId() != null && createDTO.getParentId() > 0) {
            Comment parentComment = getById(createDTO.getParentId());
            if (parentComment == null) {
                throw new BusinessException(ResultCode.COMMENT_NOT_FOUND);
            }
            if (!parentComment.getPostId().equals(createDTO.getPostId())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "父评论不属于当前笔记");
            }
            if (createDTO.getReplyUserId() == null || createDTO.getReplyUserId() <= 0) {
                createDTO.setReplyUserId(parentComment.getUserId());
            }
        }

        // 创建评论
        Comment comment = new Comment();
        comment.setPostId(createDTO.getPostId());
        comment.setUserId(userId);
        comment.setContent(createDTO.getContent());
        comment.setParentId(createDTO.getParentId() != null ? createDTO.getParentId() : 0L);
        comment.setReplyUserId(createDTO.getReplyUserId() != null ? createDTO.getReplyUserId() : 0L);
        comment.setLikeCount(0);
        comment.setStatus(1);
        comment.setDeleted(0);

        save(comment);

        // 更新笔记评论数
        postService.update(new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, createDTO.getPostId())
                .setSql("comment_count = comment_count + 1"));

        if (comment.getParentId() != null && comment.getParentId() > 0) {
            notificationService.createCommentReplyNotification(userId, comment);
        } else {
            notificationService.createPostCommentNotification(userId, post, comment);
        }

        log.info("评论创建成功，ID：{}，笔记ID：{}", comment.getId(), createDTO.getPostId());

        return convertToCommentVO(comment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long userId, Long commentId) {
        Comment comment = getById(commentId);
        if (comment == null) {
            throw new BusinessException(ResultCode.COMMENT_NOT_FOUND);
        }

        // 验证权限
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.POST_NO_PERMISSION);
        }

        List<Comment> commentsToDelete = getCommentSubtree(comment);

        List<Long> commentIds = commentsToDelete.stream()
                .map(Comment::getId)
                .collect(Collectors.toList());

        // 删除评论及其回复（逻辑删除）
        removeByIds(commentIds);

        // 删除评论的点赞行为
        removeCommentActions(commentIds);

        // 更新笔记评论数
        postService.update(new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, comment.getPostId())
                .setSql("comment_count = GREATEST(comment_count - " + commentIds.size() + ", 0)"));

        log.info("评论删除成功，ID：{}，级联删除评论数：{}", commentId, commentIds.size());
    }

    @Override
    public IPage<CommentVO> getCommentsByPostId(Long postId, CommentQueryDTO queryDTO) {
        Page<Comment> page = new Page<>(queryDTO.getPageNumSafe(), queryDTO.getPageSizeSafe());

        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<Comment>()
                .eq(Comment::getPostId, postId)
                .eq(Comment::getParentId, 0)
                .eq(Comment::getStatus, 1)
                .orderByDesc(Comment::getCreateTime);

        IPage<Comment> commentPage = page(page, wrapper);
        return commentPage.convert(this::convertToCommentVO);
    }

    @Override
    public IPage<CommentVO> getRepliesByCommentId(Long commentId, CommentQueryDTO queryDTO) {
        Comment rootComment = getById(commentId);
        if (rootComment == null) {
            throw new BusinessException(ResultCode.COMMENT_NOT_FOUND);
        }

        List<Comment> replies = getDescendantComments(commentId);
        replies.sort(Comparator.comparing(Comment::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Comment::getId));

        long pageNum = queryDTO.getPageNumSafe();
        long pageSize = queryDTO.getPageSizeSafe();
        int fromIndex = (int) Math.min((pageNum - 1) * pageSize, replies.size());
        int toIndex = (int) Math.min(fromIndex + pageSize, replies.size());

        Page<CommentVO> page = new Page<>(pageNum, pageSize);
        page.setTotal(replies.size());
        page.setRecords(replies.subList(fromIndex, toIndex).stream()
                .map(this::convertToCommentVO)
                .collect(Collectors.toList()));
        return page;
    }

    private List<Comment> getCommentSubtree(Comment rootComment) {
        List<Comment> comments = new ArrayList<>();
        comments.add(rootComment);
        comments.addAll(getDescendantComments(rootComment.getId(), false));
        return comments;
    }

    private List<Comment> getDescendantComments(Long commentId) {
        return getDescendantComments(commentId, true);
    }

    private List<Comment> getDescendantComments(Long commentId, boolean normalOnly) {
        List<Comment> descendants = new ArrayList<>();
        Set<Long> currentParentIds = new HashSet<>();
        Set<Long> visitedParentIds = new HashSet<>();
        currentParentIds.add(commentId);

        while (!currentParentIds.isEmpty()) {
            visitedParentIds.addAll(currentParentIds);
            LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<Comment>()
                    .in(Comment::getParentId, currentParentIds);
            if (normalOnly) {
                wrapper.eq(Comment::getStatus, 1);
            }
            List<Comment> children = list(wrapper);
            if (children.isEmpty()) {
                break;
            }

            descendants.addAll(children);
            currentParentIds = children.stream()
                    .map(Comment::getId)
                    .filter(id -> !visitedParentIds.contains(id))
                    .collect(Collectors.toSet());
        }

        return descendants;
    }

    private void removeCommentActions(List<Long> commentIds) {
        if (commentIds.isEmpty()) {
            return;
        }
        userActionMapper.delete(new LambdaQueryWrapper<com.xiaohongshu.interact.entity.UserAction>()
                .eq(com.xiaohongshu.interact.entity.UserAction::getTargetType, 2)
                .in(com.xiaohongshu.interact.entity.UserAction::getTargetId, commentIds));
    }

    /**
     * 将Comment实体转换为CommentVO
     */
    private CommentVO convertToCommentVO(Comment comment) {
        CommentVO vo = new CommentVO();
        BeanUtil.copyProperties(comment, vo);

        // 获取评论用户信息
        try {
            User user = userService.getById(comment.getUserId());
            if (user != null) {
                vo.setUserNickname(user.getNickname());
                vo.setUserAvatar(user.getAvatar());
            }
        } catch (Exception e) {
            log.warn("获取评论用户信息失败：{}", e.getMessage());
        }

        // 获取回复用户信息
        if (comment.getReplyUserId() != null && comment.getReplyUserId() > 0) {
            try {
                User replyUser = userService.getById(comment.getReplyUserId());
                if (replyUser != null) {
                    vo.setReplyUserNickname(replyUser.getNickname());
                }
            } catch (Exception e) {
                log.warn("获取回复用户信息失败：{}", e.getMessage());
            }
        }

        // 如果是一级评论，查询回复数量
        if (comment.getParentId() != null && comment.getParentId() == 0) {
            vo.setReplyCount(getDescendantComments(comment.getId()).size());
        }

        return vo;
    }
}
