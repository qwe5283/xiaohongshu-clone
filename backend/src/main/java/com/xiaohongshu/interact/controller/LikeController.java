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
 * 点赞控制器
 */
@Tag(name = "点赞管理")
@RestController
@RequestMapping("/like")
@RequiredArgsConstructor
public class LikeController {

    private final UserActionService userActionService;
    private final PostService postService;
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

    /**
     * 获取指定用户的点赞笔记列表（需登录，仅查看自己的点赞）
     */
    @Operation(summary = "获取点赞笔记列表", description = "分页查询指定用户点赞的笔记列表，需要登录")
    @GetMapping("/posts/{userId}")
    public Result<Map<String, Object>> getLikedPosts(
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

        // 分页查询用户点赞的笔记ID
        IPage<Long> likedIdsPage = userActionService.getLikedPostIds(userId, pageNum, pageSize);

        // 批量获取笔记详情（保持点赞时间顺序）
        List<PostVO> posts = postService.getPostsByIds(likedIdsPage.getRecords());

        // 组装返回结果
        Map<String, Object> data = new HashMap<>();
        data.put("records", posts);
        data.put("total", likedIdsPage.getTotal());
        data.put("pageNum", likedIdsPage.getCurrent());
        data.put("pageSize", likedIdsPage.getSize());
        data.put("pages", likedIdsPage.getPages());

        return Result.success(data);
    }
}
