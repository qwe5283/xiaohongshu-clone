package com.xiaohongshu.social.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xiaohongshu.common.result.PageRequest;
import com.xiaohongshu.common.result.Result;
import com.xiaohongshu.social.service.FollowService;
import com.xiaohongshu.social.vo.FollowCountVO;
import com.xiaohongshu.social.vo.FollowUserVO;
import com.xiaohongshu.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 关注控制器
 */
@Tag(name = "关注管理")
@RestController
@RequestMapping("/follow")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;
    private final JwtUtil jwtUtil;

    /**
     * 关注/取消关注用户
     */
    @Operation(summary = "关注/取消关注用户", description = "切换对指定用户的关注状态，已关注则取消，未关注则关注")
    @PostMapping("/{userId}")
    public Result<Map<String, Object>> toggleFollow(
            @Parameter(description = "JWT认证令牌（Bearer Token）", required = true)
            @RequestHeader(value = "Authorization", required = false) String token,
            @Parameter(description = "目标用户ID", required = true, example = "2")
            @PathVariable Long userId) {
        Long currentUserId = jwtUtil.getUserIdFromToken(token);
        boolean followed = followService.toggleFollow(currentUserId, userId);

        Map<String, Object> data = new HashMap<>();
        data.put("followed", followed);
        data.put("message", followed ? "关注成功" : "取消关注成功");
        return Result.success(data);
    }

    /**
     * 获取关注状态
     */
    @Operation(summary = "获取关注状态", description = "查询当前登录用户是否已关注指定用户")
    @GetMapping("/status/{userId}")
    public Result<Map<String, Boolean>> getFollowStatus(
            @Parameter(description = "JWT认证令牌（Bearer Token）", required = true)
            @RequestHeader(value = "Authorization", required = false) String token,
            @Parameter(description = "目标用户ID", required = true, example = "2")
            @PathVariable Long userId) {
        Long currentUserId = jwtUtil.getUserIdFromToken(token);
        boolean followed = followService.isFollowing(currentUserId, userId);

        Map<String, Boolean> data = new HashMap<>();
        data.put("followed", followed);
        return Result.success(data);
    }

    /**
     * 获取用户的关注列表
     */
    @Operation(summary = "获取关注列表", description = "分页查询指定用户关注的人，支持携带Token以获取相互关注状态")
    @GetMapping("/following/{userId}")
    public Result<IPage<FollowUserVO>> getFollowingList(
            @Parameter(description = "JWT认证令牌（可选，用于获取关注状态）")
            @RequestHeader(value = "Authorization", required = false) String token,
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable Long userId,
            @Valid PageRequest queryDTO) {
        // 可选 token：已登录时用于填充 followed 字段
        Long currentUserId = null;
        try {
            currentUserId = jwtUtil.getUserIdFromToken(token);
        } catch (Exception e) {
            // token 无效或未携带，followed 字段保持 null
        }
        IPage<FollowUserVO> page = followService.getFollowingList(userId, queryDTO, currentUserId);
        return Result.success(page);
    }

    /**
     * 获取用户的粉丝列表
     */
    @Operation(summary = "获取粉丝列表", description = "分页查询指定用户的粉丝，支持携带Token以获取相互关注状态")
    @GetMapping("/followers/{userId}")
    public Result<IPage<FollowUserVO>> getFollowersList(
            @Parameter(description = "JWT认证令牌（可选，用于获取关注状态）")
            @RequestHeader(value = "Authorization", required = false) String token,
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable Long userId,
            @Valid PageRequest queryDTO) {
        Long currentUserId = null;
        try {
            currentUserId = jwtUtil.getUserIdFromToken(token);
        } catch (Exception e) {
            // token 无效或未携带
        }
        IPage<FollowUserVO> page = followService.getFollowersList(userId, queryDTO, currentUserId);
        return Result.success(page);
    }

    /**
     * 获取用户的关注数和粉丝数
     */
    @Operation(summary = "获取关注和粉丝数量", description = "查询指定用户的关注数和粉丝数")
    @GetMapping("/count/{userId}")
    public Result<FollowCountVO> getFollowCount(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable Long userId) {
        FollowCountVO countVO = followService.getFollowCount(userId);
        return Result.success(countVO);
    }
}
