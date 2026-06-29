package com.xiaohongshu.notification.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xiaohongshu.common.result.PageRequest;
import com.xiaohongshu.common.result.Result;
import com.xiaohongshu.notification.service.NotificationService;
import com.xiaohongshu.notification.vo.NotificationVO;
import com.xiaohongshu.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 消息通知控制器
 */
@Tag(name = "消息通知")
@RestController
@RequestMapping("/notification")
@RequiredArgsConstructor
@Validated
public class NotificationController {

    private final NotificationService notificationService;
    private final JwtUtil jwtUtil;

    @Operation(summary = "获取未读消息数", description = "前端可轮询该接口展示未读消息数")
    @GetMapping("/unread-count")
    public Result<Map<String, Long>> getUnreadCount(
            @Parameter(description = "JWT认证令牌（Bearer Token）", required = true)
            @RequestHeader(value = "Authorization", required = false) String token) {
        Long userId = jwtUtil.getUserIdFromToken(token);
        Map<String, Long> data = new HashMap<>();
        data.put("unreadCount", notificationService.getUnreadCount(userId));
        return Result.success(data);
    }

    @Operation(summary = "查看消息通知", description = "分页查看当前登录用户收到的消息，可按消息类型筛选")
    @GetMapping("/list")
    public Result<IPage<NotificationVO>> getNotificationPage(
            @Parameter(description = "JWT认证令牌（Bearer Token）", required = true)
            @RequestHeader(value = "Authorization", required = false) String token,
            @Parameter(description = "消息类型：1-点赞笔记，2-收藏笔记，3-评论笔记，4-回复评论，5-点赞评论，6-新增关注")
            @RequestParam(required = false)
            @Min(value = 1, message = "消息类型最小为1")
            @Max(value = 6, message = "消息类型最大为6") Integer type,
            @Valid PageRequest queryDTO) {
        Long userId = jwtUtil.getUserIdFromToken(token);
        return Result.success(notificationService.getNotificationPage(userId, queryDTO, type));
    }

    @Operation(summary = "一键已读", description = "将当前登录用户所有未读消息标记为已读")
    @PutMapping("/read-all")
    public Result<Void> markAllAsRead(
            @Parameter(description = "JWT认证令牌（Bearer Token）", required = true)
            @RequestHeader(value = "Authorization", required = false) String token) {
        Long userId = jwtUtil.getUserIdFromToken(token);
        notificationService.markAllAsRead(userId);
        return Result.success("已全部标记为已读", null);
    }
}
