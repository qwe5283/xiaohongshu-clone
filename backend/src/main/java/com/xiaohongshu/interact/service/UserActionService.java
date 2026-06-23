package com.xiaohongshu.interact.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaohongshu.interact.entity.UserAction;

import java.util.List;

/**
 * 用户行为服务接口
 */
public interface UserActionService extends IService<UserAction> {

    /**
     * 点赞/取消点赞笔记
     *
     * @param userId 用户ID
     * @param postId 笔记ID
     * @return true-点赞，false-取消点赞
     */
    boolean toggleLikePost(Long userId, Long postId);

    /**
     * 点赞/取消点赞评论
     *
     * @param userId    用户ID
     * @param commentId 评论ID
     * @return true-点赞，false-取消点赞
     */
    boolean toggleLikeComment(Long userId, Long commentId);

    /**
     * 收藏/取消收藏笔记
     *
     * @param userId 用户ID
     * @param postId 笔记ID
     * @return true-收藏，false-取消收藏
     */
    boolean toggleCollectPost(Long userId, Long postId);

    /**
     * 查询当前用户是否已点赞笔记
     *
     * @param userId 用户ID
     * @param postId 笔记ID
     * @return true-已点赞
     */
    boolean isLikedPost(Long userId, Long postId);

    /**
     * 查询当前用户是否已点赞评论
     *
     * @param userId    用户ID
     * @param commentId 评论ID
     * @return true-已点赞
     */
    boolean isLikedComment(Long userId, Long commentId);

    /**
     * 查询当前用户是否已收藏笔记
     *
     * @param userId 用户ID
     * @param postId 笔记ID
     * @return true-已收藏
     */
    boolean isCollectedPost(Long userId, Long postId);

    /**
     * 批量查询用户对多个笔记的点赞状态
     *
     * @param userId  用户ID
     * @param postIds 笔记ID列表
     * @return 已点赞的笔记ID列表
     */
    List<Long> getLikedPostIds(Long userId, List<Long> postIds);

    /**
     * 批量查询用户对多个笔记的收藏状态
     *
     * @param userId  用户ID
     * @param postIds 笔记ID列表
     * @return 已收藏的笔记ID列表
     */
    List<Long> getCollectedPostIds(Long userId, List<Long> postIds);
}
