package com.xiaohongshu.interact.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xiaohongshu.common.result.Result;
import com.xiaohongshu.interact.dto.CommentCreateDTO;
import com.xiaohongshu.interact.dto.CommentQueryDTO;
import com.xiaohongshu.interact.service.CommentService;
import com.xiaohongshu.interact.vo.CommentVO;
import com.xiaohongshu.security.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 评论控制器
 */
@RestController
@RequestMapping("/comment")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;
    private final JwtUtil jwtUtil;

    /**
     * 发表评论
     */
    @PostMapping("/create")
    public Result<CommentVO> createComment(
            @RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody CommentCreateDTO createDTO) {
        Long userId = jwtUtil.getUserIdFromToken(token);
        CommentVO commentVO = commentService.createComment(userId, createDTO);
        return Result.success("评论成功", commentVO);
    }

    /**
     * 删除评论
     */
    @DeleteMapping("/delete/{commentId}")
    public Result<Void> deleteComment(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long commentId) {
        Long userId = jwtUtil.getUserIdFromToken(token);
        commentService.deleteComment(userId, commentId);
        return Result.success("评论删除成功", null);
    }

    /**
     * 获取笔记的评论列表（一级评论）
     */
    @GetMapping("/post/{postId}")
    public Result<IPage<CommentVO>> getCommentsByPostId(
            @PathVariable Long postId,
            @Valid CommentQueryDTO queryDTO) {
        IPage<CommentVO> page = commentService.getCommentsByPostId(postId, queryDTO);
        return Result.success(page);
    }

    /**
     * 获取评论的回复列表
     */
    @GetMapping("/replies/{commentId}")
    public Result<IPage<CommentVO>> getRepliesByCommentId(
            @PathVariable Long commentId,
            @Valid CommentQueryDTO queryDTO) {
        IPage<CommentVO> page = commentService.getRepliesByCommentId(commentId, queryDTO);
        return Result.success(page);
    }
}
