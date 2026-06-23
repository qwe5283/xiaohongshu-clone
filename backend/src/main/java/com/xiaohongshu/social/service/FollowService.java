package com.xiaohongshu.social.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaohongshu.common.result.PageRequest;
import com.xiaohongshu.social.entity.UserFollow;
import com.xiaohongshu.social.vo.FollowCountVO;
import com.xiaohongshu.social.vo.FollowUserVO;

/**
 * 用户关注服务接口
 */
public interface FollowService extends IService<UserFollow> {

    /**
     * 关注/取消关注用户
     *
     * @param userId       当前用户ID
     * @param followUserId 被关注用户ID
     * @return true-关注，false-取消关注
     */
    boolean toggleFollow(Long userId, Long followUserId);

    /**
     * 查询是否已关注某用户
     *
     * @param userId       当前用户ID
     * @param followUserId 目标用户ID
     * @return true-已关注
     */
    boolean isFollowing(Long userId, Long followUserId);

    /**
     * 获取用户的关注列表
     *
     * @param userId    用户ID
     * @param queryDTO  分页参数
     * @return 关注用户列表
     */
    IPage<FollowUserVO> getFollowingList(Long userId, PageRequest queryDTO);

    /**
     * 获取用户的粉丝列表
     *
     * @param userId    用户ID
     * @param queryDTO  分页参数
     * @return 粉丝用户列表
     */
    IPage<FollowUserVO> getFollowersList(Long userId, PageRequest queryDTO);

    /**
     * 获取用户的关注数和粉丝数
     *
     * @param userId 用户ID
     * @return 关注数和粉丝数
     */
    FollowCountVO getFollowCount(Long userId);
}
