package com.xiaohongshu.post.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xiaohongshu.common.result.Result;
import com.xiaohongshu.post.dto.PostCreateDTO;
import com.xiaohongshu.post.dto.PostQueryDTO;
import com.xiaohongshu.post.dto.PostUpdateDTO;
import com.xiaohongshu.security.JwtUtil;
import com.xiaohongshu.post.service.PostService;
import com.xiaohongshu.post.vo.PostVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 笔记控制器
 */
@RestController
@RequestMapping("/post")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final JwtUtil jwtUtil;

    /**
     * 创建笔记
     */
    @PostMapping("/create")
    public Result<PostVO> createPost(
            @RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody PostCreateDTO createDTO) {
        Long userId = jwtUtil.getUserIdFromToken(token);
        PostVO postVO = postService.createPost(userId, createDTO);
        return Result.success("笔记发布成功", postVO);
    }

    /**
     * 更新笔记
     */
    @PutMapping("/update")
    public Result<PostVO> updatePost(
            @RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody PostUpdateDTO updateDTO) {
        Long userId = jwtUtil.getUserIdFromToken(token);
        PostVO postVO = postService.updatePost(userId, updateDTO);
        return Result.success("笔记更新成功", postVO);
    }

    /**
     * 删除笔记
     */
    @DeleteMapping("/delete/{postId}")
    public Result<Void> deletePost(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long postId) {
        Long userId = jwtUtil.getUserIdFromToken(token);
        postService.deletePost(userId, postId);
        return Result.success("笔记删除成功", null);
    }

    /**
     * 获取笔记详情
     */
    @GetMapping("/{postId}")
    public Result<PostVO> getPostById(@PathVariable Long postId) {
        PostVO postVO = postService.getPostById(postId);
        // 增加浏览量
        postService.incrementViewCount(postId);
        return Result.success(postVO);
    }

    /**
     * 分页查询笔记列表
     */
    @GetMapping("/list")
    public Result<IPage<PostVO>> getPostPage(@Valid PostQueryDTO queryDTO) {
        IPage<PostVO> page = postService.getPostPage(queryDTO);
        return Result.success(page);
    }

    /**
     * 获取用户的笔记列表
     */
    @GetMapping("/user/{userId}")
    public Result<IPage<PostVO>> getUserPosts(
            @PathVariable Long userId,
            @Valid PostQueryDTO queryDTO) {
        IPage<PostVO> page = postService.getUserPosts(userId, queryDTO);
        return Result.success(page);
    }

    /**
     * 获取当前用户的笔记列表
     */
    @GetMapping("/my")
    public Result<IPage<PostVO>> getMyPosts(
            @RequestHeader(value = "Authorization", required = false) String token,
            @Valid PostQueryDTO queryDTO) {
        Long userId = jwtUtil.getUserIdFromToken(token);
        IPage<PostVO> page = postService.getUserPosts(userId, queryDTO);
        return Result.success(page);
    }
}
