package com.xiaohongshu.interact.controller;

import com.xiaohongshu.common.result.Result;
import com.xiaohongshu.interact.service.UserActionService;
import com.xiaohongshu.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 点赞控制器
 */
@Tag(name = "点赞管理")
@RestController
@RequestMapping("/like")
@RequiredArgsConstructor
public class LikeController {

    private final UserActionService userActionService;
    private final JwtUtil jwtUtil;

    /**
     * 点赞/取消点赞笔记
     */
    @Operation(summary = "点赞/取消点赞笔记", description = "切换对指定笔记的点赞状态，已点赞则取消，未点赞则点赞")
    @PostMapping("/post/{postId}")
    public Result<Map<String, Object>> toggleLikePost(
            @Parameter(description = "JWT认证令牌（Bearer Token）", required = true)
            @RequestHeader(value = "Authorization", required = false) String token,
            @Parameter(description = "笔记ID", required = true, example = "1")
            @PathVariable Long postId) {
        Long userId = jwtUtil.getUserIdFromToken(token);
        boolean liked = userActionService.toggleLikePost(userId, postId);

        Map<String, Object> data = new HashMap<>();
        data.put("liked", liked);
        data.put("message", liked ? "点赞成功" : "取消点赞成功");
        return Result.success(data);
    }

    /**
     * 点赞/取消点赞评论
     */
    @Operation(summary = "点赞/取消点赞评论", description = "切换对指定评论的点赞状态，已点赞则取消，未点赞则点赞")
    @PostMapping("/comment/{commentId}")
    public Result<Map<String, Object>> toggleLikeComment(
            @Parameter(description = "JWT认证令牌（Bearer Token）", required = true)
            @RequestHeader(value = "Authorization", required = false) String token,
            @Parameter(description = "评论ID", required = true, example = "1")
            @PathVariable Long commentId) {
        Long userId = jwtUtil.getUserIdFromToken(token);
        boolean liked = userActionService.toggleLikeComment(userId, commentId);

        Map<String, Object> data = new HashMap<>();
        data.put("liked", liked);
        data.put("message", liked ? "点赞成功" : "取消点赞成功");
        return Result.success(data);
    }

    /**
     * 获取笔记点赞状态
     */
    @Operation(summary = "获取笔记点赞状态", description = "查询当前登录用户是否已点赞指定笔记")
    @GetMapping("/status/post/{postId}")
    public Result<Map<String, Boolean>> getLikeStatusPost(
            @Parameter(description = "JWT认证令牌（Bearer Token）", required = true)
            @RequestHeader(value = "Authorization", required = false) String token,
            @Parameter(description = "笔记ID", required = true, example = "1")
            @PathVariable Long postId) {
        Long userId = jwtUtil.getUserIdFromToken(token);
        boolean liked = userActionService.isLikedPost(userId, postId);

        Map<String, Boolean> data = new HashMap<>();
        data.put("liked", liked);
        return Result.success(data);
    }

    /**
     * 获取评论点赞状态
     */
    @Operation(summary = "获取评论点赞状态", description = "查询当前登录用户是否已点赞指定评论")
    @GetMapping("/status/comment/{commentId}")
    public Result<Map<String, Boolean>> getLikeStatusComment(
            @Parameter(description = "JWT认证令牌（Bearer Token）", required = true)
            @RequestHeader(value = "Authorization", required = false) String token,
            @Parameter(description = "评论ID", required = true, example = "1")
            @PathVariable Long commentId) {
        Long userId = jwtUtil.getUserIdFromToken(token);
        boolean liked = userActionService.isLikedComment(userId, commentId);

        Map<String, Boolean> data = new HashMap<>();
        data.put("liked", liked);
        return Result.success(data);
    }
}
