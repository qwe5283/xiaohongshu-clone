package com.xiaohongshu.interact.controller;

import com.xiaohongshu.common.result.Result;
import com.xiaohongshu.interact.service.UserActionService;
import com.xiaohongshu.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 收藏控制器
 */
@RestController
@RequestMapping("/collect")
@RequiredArgsConstructor
public class CollectController {

    private final UserActionService userActionService;
    private final JwtUtil jwtUtil;

    /**
     * 收藏/取消收藏笔记
     */
    @PostMapping("/post/{postId}")
    public Result<Map<String, Object>> toggleCollectPost(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long postId) {
        Long userId = jwtUtil.getUserIdFromToken(token);
        boolean collected = userActionService.toggleCollectPost(userId, postId);

        Map<String, Object> data = new HashMap<>();
        data.put("collected", collected);
        data.put("message", collected ? "收藏成功" : "取消收藏成功");
        return Result.success(data);
    }

    /**
     * 获取笔记收藏状态
     */
    @GetMapping("/status/post/{postId}")
    public Result<Map<String, Boolean>> getCollectStatusPost(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long postId) {
        Long userId = jwtUtil.getUserIdFromToken(token);
        boolean collected = userActionService.isCollectedPost(userId, postId);

        Map<String, Boolean> data = new HashMap<>();
        data.put("collected", collected);
        return Result.success(data);
    }
}
