package com.xiaohongshu.interact.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xiaohongshu.common.result.Result;
import com.xiaohongshu.interact.dto.CommentCreateDTO;
import com.xiaohongshu.interact.dto.CommentQueryDTO;
import com.xiaohongshu.interact.service.CommentService;
import com.xiaohongshu.interact.vo.CommentVO;
import com.xiaohongshu.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 评论控制器
 */
@Tag(name = "评论管理")
@RestController
@RequestMapping("/comment")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;
    private final JwtUtil jwtUtil;

    /**
     * 发表评论
     */
    @Operation(summary = "发表评论", description = "对指定笔记发表评论，支持一级评论和回复其他评论")
    @PostMapping("/create")
    public Result<CommentVO> createComment(
            @Parameter(description = "JWT认证令牌（Bearer Token）", required = true)
            @RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody CommentCreateDTO createDTO) {
        Long userId = jwtUtil.getUserIdFromToken(token);
        CommentVO commentVO = commentService.createComment(userId, createDTO);
        return Result.success("评论成功", commentVO);
    }

    /**
     * 删除评论
     */
    @Operation(summary = "删除评论", description = "删除自己发表的评论")
    @DeleteMapping("/delete/{commentId}")
    public Result<Void> deleteComment(
            @Parameter(description = "JWT认证令牌（Bearer Token）", required = true)
            @RequestHeader(value = "Authorization", required = false) String token,
            @Parameter(description = "评论ID", required = true, example = "1")
            @PathVariable Long commentId) {
        Long userId = jwtUtil.getUserIdFromToken(token);
        commentService.deleteComment(userId, commentId);
        return Result.success("评论删除成功", null);
    }

    /**
     * 获取笔记的评论列表（一级评论）
     */
    @Operation(summary = "获取笔记评论列表", description = "分页查询指定笔记的一级评论")
    @GetMapping("/post/{postId}")
    public Result<IPage<CommentVO>> getCommentsByPostId(
            @Parameter(description = "JWT认证令牌（Bearer Token），可选")
            @RequestHeader(value = "Authorization", required = false) String token,
            @Parameter(description = "笔记ID", required = true, example = "1")
            @PathVariable Long postId,
            @Valid CommentQueryDTO queryDTO) {
        Long currentUserId = getOptionalUserId(token);
        IPage<CommentVO> page = commentService.getCommentsByPostId(currentUserId, postId, queryDTO);
        return Result.success(page);
    }

    /**
     * 获取评论的回复列表
     */
    @Operation(summary = "获取评论回复列表", description = "分页查询指定评论的所有回复（二级评论）")
    @GetMapping("/replies/{commentId}")
    public Result<IPage<CommentVO>> getRepliesByCommentId(
            @Parameter(description = "JWT认证令牌（Bearer Token），可选")
            @RequestHeader(value = "Authorization", required = false) String token,
            @Parameter(description = "父评论ID", required = true, example = "1")
            @PathVariable Long commentId,
            @Valid CommentQueryDTO queryDTO) {
        Long currentUserId = getOptionalUserId(token);
        IPage<CommentVO> page = commentService.getRepliesByCommentId(currentUserId, commentId, queryDTO);
        return Result.success(page);
    }

    private Long getOptionalUserId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return jwtUtil.validateToken(token) ? jwtUtil.getUserIdFromToken(token) : null;
    }
}
