package com.xiaohongshu.social.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xiaohongshu.common.result.PageRequest;
import com.xiaohongshu.common.result.Result;
import com.xiaohongshu.social.service.FollowService;
import com.xiaohongshu.social.vo.FollowCountVO;
import com.xiaohongshu.social.vo.FollowUserVO;
import com.xiaohongshu.security.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 关注控制器
 */
@RestController
@RequestMapping("/follow")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;
    private final JwtUtil jwtUtil;

    /**
     * 关注/取消关注用户
     */
    @PostMapping("/{userId}")
    public Result<Map<String, Object>> toggleFollow(
            @RequestHeader(value = "Authorization", required = false) String token,
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
    @GetMapping("/status/{userId}")
    public Result<Map<String, Boolean>> getFollowStatus(
            @RequestHeader(value = "Authorization", required = false) String token,
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
    @GetMapping("/following/{userId}")
    public Result<IPage<FollowUserVO>> getFollowingList(
            @RequestHeader(value = "Authorization", required = false) String token,
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
    @GetMapping("/followers/{userId}")
    public Result<IPage<FollowUserVO>> getFollowersList(
            @RequestHeader(value = "Authorization", required = false) String token,
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
    @GetMapping("/count/{userId}")
    public Result<FollowCountVO> getFollowCount(@PathVariable Long userId) {
        FollowCountVO countVO = followService.getFollowCount(userId);
        return Result.success(countVO);
    }
}
