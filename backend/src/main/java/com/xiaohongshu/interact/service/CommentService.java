package com.xiaohongshu.interact.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaohongshu.interact.dto.CommentCreateDTO;
import com.xiaohongshu.interact.dto.CommentQueryDTO;
import com.xiaohongshu.interact.entity.Comment;
import com.xiaohongshu.interact.vo.CommentVO;

/**
 * 评论服务接口
 */
public interface CommentService extends IService<Comment> {

    /**
     * 发表评论
     *
     * @param userId    用户ID
     * @param createDTO 评论信息
     * @return 评论详情
     */
    CommentVO createComment(Long userId, CommentCreateDTO createDTO);

    /**
     * 删除评论
     *
     * @param userId    用户ID
     * @param commentId 评论ID
     */
    void deleteComment(Long userId, Long commentId);

    /**
     * 获取笔记的一级评论列表（分页）
     *
     * @param postId    笔记ID
     * @param queryDTO  分页参数
     * @return 评论列表
     */
    IPage<CommentVO> getCommentsByPostId(Long postId, CommentQueryDTO queryDTO);

    /**
     * 获取评论的回复列表（分页）
     *
     * @param commentId 父评论ID
     * @param queryDTO  分页参数
     * @return 回复列表
     */
    IPage<CommentVO> getRepliesByCommentId(Long commentId, CommentQueryDTO queryDTO);
}
