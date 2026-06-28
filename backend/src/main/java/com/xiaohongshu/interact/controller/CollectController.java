package com.xiaohongshu.interact.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xiaohongshu.common.result.Result;
import com.xiaohongshu.interact.service.UserActionService;
import com.xiaohongshu.post.service.PostService;
import com.xiaohongshu.post.vo.PostVO;
import com.xiaohongshu.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 收藏控制器
 */
@Tag(name = "收藏管理")
@RestController
@RequestMapping("/collect")
@RequiredArgsConstructor
public class CollectController {

    private final UserActionService userActionService;
    private final PostService postService;
    private final JwtUtil jwtUtil;

    /**
     * 收藏/取消收藏笔记
     */
    @Operation(summary = "收藏/取消收藏笔记", description = "切换对指定笔记的收藏状态，已收藏则取消，未收藏则收藏")
    @PostMapping("/post/{postId}")
    public Result<Map<String, Object>> toggleCollectPost(
            @Parameter(description = "JWT认证令牌（Bearer Token）", required = true)
            @RequestHeader(value = "Authorization", required = false) String token,
            @Parameter(description = "笔记ID", required = true, example = "1")
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
    @Operation(summary = "获取笔记收藏状态", description = "查询当前登录用户是否已收藏指定笔记")
    @GetMapping("/status/post/{postId}")
    public Result<Map<String, Boolean>> getCollectStatusPost(
            @Parameter(description = "JWT认证令牌（Bearer Token）", required = true)
            @RequestHeader(value = "Authorization", required = false) String token,
            @Parameter(description = "笔记ID", required = true, example = "1")
            @PathVariable Long postId) {
        Long userId = jwtUtil.getUserIdFromToken(token);
        boolean collected = userActionService.isCollectedPost(userId, postId);

        Map<String, Boolean> data = new HashMap<>();
        data.put("collected", collected);
        return Result.success(data);
    }

    /**
     * 获取指定用户的收藏笔记列表（需登录，可查看他人收藏）
     */
    @Operation(summary = "获取收藏笔记列表", description = "分页查询指定用户收藏的笔记列表，需要登录")
    @GetMapping("/posts/{userId}")
    public Result<Map<String, Object>> getCollectedPosts(
            @Parameter(description = "JWT认证令牌（Bearer Token）", required = true)
            @RequestHeader(value = "Authorization", required = false) String token,
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable Long userId,
            @Parameter(description = "页码，从1开始", example = "1")
            @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量，最大100", example = "10")
            @RequestParam(defaultValue = "10") Integer pageSize) {
        // 验证登录状态
        jwtUtil.getUserIdFromToken(token);

        // 分页查询用户收藏的笔记ID
        IPage<Long> collectedIdsPage = userActionService.getCollectedPostIds(userId, pageNum, pageSize);

        // 批量获取笔记详情（保持收藏时间顺序）
        List<PostVO> posts = postService.getPostsByIds(collectedIdsPage.getRecords());

        // 组装返回结果
        Map<String, Object> data = new HashMap<>();
        data.put("records", posts);
        data.put("total", collectedIdsPage.getTotal());
        data.put("pageNum", collectedIdsPage.getCurrent());
        data.put("pageSize", collectedIdsPage.getSize());
        data.put("pages", collectedIdsPage.getPages());

        return Result.success(data);
    }
}
