package com.xiaohongshu.post.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xiaohongshu.common.result.Result;
import com.xiaohongshu.post.dto.PostCreateDTO;
import com.xiaohongshu.post.dto.PostQueryDTO;
import com.xiaohongshu.post.dto.PostUpdateDTO;
import com.xiaohongshu.security.JwtUtil;
import com.xiaohongshu.post.service.PostService;
import com.xiaohongshu.post.vo.PostVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 笔记控制器
 */
@Tag(name = "笔记管理")
@RestController
@RequestMapping("/post")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final JwtUtil jwtUtil;

    /**
     * 创建笔记
     */
    @Operation(summary = "创建笔记", description = "发布一篇新笔记，支持图文和视频类型")
    @PostMapping("/create")
    public Result<PostVO> createPost(
            @Parameter(description = "JWT认证令牌（Bearer Token）", required = true)
            @RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody PostCreateDTO createDTO) {
        Long userId = jwtUtil.getUserIdFromToken(token);
        PostVO postVO = postService.createPost(userId, createDTO);
        return Result.success("笔记发布成功", postVO);
    }

    /**
     * 更新笔记
     */
    @Operation(summary = "更新笔记", description = "修改自己已发布的笔记内容")
    @PutMapping("/update")
    public Result<PostVO> updatePost(
            @Parameter(description = "JWT认证令牌（Bearer Token）", required = true)
            @RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody PostUpdateDTO updateDTO) {
        Long userId = jwtUtil.getUserIdFromToken(token);
        PostVO postVO = postService.updatePost(userId, updateDTO);
        return Result.success("笔记更新成功", postVO);
    }

    /**
     * 删除笔记
     */
    @Operation(summary = "删除笔记", description = "删除自己已发布的笔记（逻辑删除）")
    @DeleteMapping("/delete/{postId}")
    public Result<Void> deletePost(
            @Parameter(description = "JWT认证令牌（Bearer Token）", required = true)
            @RequestHeader(value = "Authorization", required = false) String token,
            @Parameter(description = "笔记ID", required = true, example = "1")
            @PathVariable Long postId) {
        Long userId = jwtUtil.getUserIdFromToken(token);
        postService.deletePost(userId, postId);
        return Result.success("笔记删除成功", null);
    }

    /**
     * 获取笔记详情
     */
    @Operation(summary = "获取笔记详情", description = "根据笔记ID获取完整笔记信息，同时增加浏览量")
    @GetMapping("/{postId}")
    public Result<PostVO> getPostById(
            @Parameter(description = "笔记ID", required = true, example = "1")
            @PathVariable Long postId) {
        PostVO postVO = postService.getPostById(postId);
        // 增加浏览量
        postService.incrementViewCount(postId);
        return Result.success(postVO);
    }

    /**
     * 分页查询笔记列表
     */
    @Operation(summary = "分页查询笔记列表", description = "支持关键词搜索、按类型/状态筛选、按最新/最热排序。可携带Token获取点赞状态。")
    @GetMapping("/list")
    public Result<IPage<PostVO>> getPostPage(
            @Parameter(description = "JWT认证令牌（可选，用于获取点赞状态）")
            @RequestHeader(value = "Authorization", required = false) String token,
            @Valid PostQueryDTO queryDTO) {
        Long userId = null;
        if (token != null) {
            try {
                userId = jwtUtil.getUserIdFromToken(token);
            } catch (Exception ignored) {
                // token无效时当作未登录处理
            }
        }
        IPage<PostVO> page = postService.getPostPage(queryDTO, userId);
        return Result.success(page);
    }

    /**
     * 获取用户的笔记列表
     */
    @Operation(summary = "获取指定用户的笔记列表", description = "分页查询某个用户发布的所有笔记")
    @GetMapping("/user/{userId}")
    public Result<IPage<PostVO>> getUserPosts(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable Long userId,
            @Valid PostQueryDTO queryDTO) {
        IPage<PostVO> page = postService.getUserPosts(userId, queryDTO);
        return Result.success(page);
    }

    /**
     * 获取当前用户的笔记列表
     */
    @Operation(summary = "获取我的笔记列表", description = "分页查询当前登录用户发布的所有笔记")
    @GetMapping("/my")
    public Result<IPage<PostVO>> getMyPosts(
            @Parameter(description = "JWT认证令牌（Bearer Token）", required = true)
            @RequestHeader(value = "Authorization", required = false) String token,
            @Valid PostQueryDTO queryDTO) {
        Long userId = jwtUtil.getUserIdFromToken(token);
        IPage<PostVO> page = postService.getUserPosts(userId, queryDTO);
        return Result.success(page);
    }
}
