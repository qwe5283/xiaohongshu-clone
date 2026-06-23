package com.xiaohongshu.interact.controller;

import com.xiaohongshu.common.result.Result;
import com.xiaohongshu.interact.service.UserActionService;
import com.xiaohongshu.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 点赞控制器
 */
@RestController
@RequestMapping("/like")
@RequiredArgsConstructor
public class LikeController {

    private final UserActionService userActionService;
    private final JwtUtil jwtUtil;

    /**
     * 点赞/取消点赞笔记
     */
    @PostMapping("/post/{postId}")
    public Result<Map<String, Object>> toggleLikePost(
            @RequestHeader(value = "Authorization", required = false) String token,
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
    @PostMapping("/comment/{commentId}")
    public Result<Map<String, Object>> toggleLikeComment(
            @RequestHeader(value = "Authorization", required = false) String token,
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
    @GetMapping("/status/post/{postId}")
    public Result<Map<String, Boolean>> getLikeStatusPost(
            @RequestHeader(value = "Authorization", required = false) String token,
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
    @GetMapping("/status/comment/{commentId}")
    public Result<Map<String, Boolean>> getLikeStatusComment(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long commentId) {
        Long userId = jwtUtil.getUserIdFromToken(token);
        boolean liked = userActionService.isLikedComment(userId, commentId);

        Map<String, Boolean> data = new HashMap<>();
        data.put("liked", liked);
        return Result.success(data);
    }
}
