package com.xiaohongshu.interact.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xiaohongshu.common.result.Result;
import com.xiaohongshu.interact.service.UserActionService;
import com.xiaohongshu.post.service.PostService;
import com.xiaohongshu.post.vo.PostVO;
import com.xiaohongshu.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 收藏控制器
 */
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

    /**
     * 获取指定用户的收藏笔记列表（需登录，可查看他人收藏）
     */
    @GetMapping("/posts/{userId}")
    public Result<Map<String, Object>> getCollectedPosts(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
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
