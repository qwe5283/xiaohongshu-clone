package com.xiaohongshu.social.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaohongshu.common.result.PageRequest;
import com.xiaohongshu.common.exception.BusinessException;
import com.xiaohongshu.common.result.ResultCode;
import com.xiaohongshu.social.entity.UserFollow;
import com.xiaohongshu.social.mapper.UserFollowMapper;
import com.xiaohongshu.social.service.FollowService;
import com.xiaohongshu.social.vo.FollowCountVO;
import com.xiaohongshu.social.vo.FollowUserVO;
import com.xiaohongshu.user.entity.User;
import com.xiaohongshu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户关注服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowServiceImpl extends ServiceImpl<UserFollowMapper, UserFollow> implements FollowService {

    private final UserService userService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleFollow(Long userId, Long followUserId) {
        // 不能关注自己
        if (userId.equals(followUserId)) {
            throw new BusinessException(ResultCode.FOLLOW_SELF);
        }

        // 验证被关注用户是否存在
        User targetUser = userService.getById(followUserId);
        if (targetUser == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 查询是否已关注
        UserFollow existing = getOne(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getUserId, userId)
                .eq(UserFollow::getFollowUserId, followUserId));

        if (existing != null) {
            // 已关注，取消关注
            boolean removed = removeById(existing.getId());
            if (!removed) {
                log.info("关注关系已被其他请求取消，用户ID：{}，被关注用户ID：{}", userId, followUserId);
                return false;
            }

            // 更新用户的关注数和粉丝数
            userService.update(new LambdaUpdateWrapper<User>()
                    .eq(User::getId, userId)
                    .setSql("following_count = GREATEST(following_count - 1, 0)"));
            userService.update(new LambdaUpdateWrapper<User>()
                    .eq(User::getId, followUserId)
                    .setSql("fans_count = GREATEST(fans_count - 1, 0)"));

            log.info("取消关注成功，用户ID：{}，被关注用户ID：{}", userId, followUserId);
            return false;
        } else {
            // 未关注，新增关注
            UserFollow follow = new UserFollow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            try {
                save(follow);
            } catch (DuplicateKeyException e) {
                log.info("关注关系已被其他请求创建，用户ID：{}，被关注用户ID：{}", userId, followUserId);
                return true;
            }

            // 更新用户的关注数和粉丝数
            userService.update(new LambdaUpdateWrapper<User>()
                    .eq(User::getId, userId)
                    .setSql("following_count = following_count + 1"));
            userService.update(new LambdaUpdateWrapper<User>()
                    .eq(User::getId, followUserId)
                    .setSql("fans_count = fans_count + 1"));

            log.info("关注成功，用户ID：{}，被关注用户ID：{}", userId, followUserId);
            return true;
        }
    }

    @Override
    public boolean isFollowing(Long userId, Long followUserId) {
        return count(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getUserId, userId)
                .eq(UserFollow::getFollowUserId, followUserId)) > 0;
    }

    @Override
    public IPage<FollowUserVO> getFollowingList(Long userId, PageRequest queryDTO, Long currentUserId) {
        Page<UserFollow> page = new Page<>(queryDTO.getPageNumSafe(), queryDTO.getPageSizeSafe());

        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getUserId, userId)
                .orderByDesc(UserFollow::getCreateTime);

        IPage<UserFollow> followPage = page(page, wrapper);

        return followPage.convert(follow -> convertToFollowUserVO(follow, true, currentUserId));
    }

    @Override
    public IPage<FollowUserVO> getFollowersList(Long userId, PageRequest queryDTO, Long currentUserId) {
        Page<UserFollow> page = new Page<>(queryDTO.getPageNumSafe(), queryDTO.getPageSizeSafe());

        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowUserId, userId)
                .orderByDesc(UserFollow::getCreateTime);

        IPage<UserFollow> followPage = page(page, wrapper);

        return followPage.convert(follow -> convertToFollowUserVO(follow, false, currentUserId));
    }

    @Override
    public FollowCountVO getFollowCount(Long userId) {
        FollowCountVO countVO = new FollowCountVO();

        // 关注数
        long followingCount = count(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getUserId, userId));
        countVO.setFollowingCount(followingCount);

        // 粉丝数
        long followersCount = count(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowUserId, userId));
        countVO.setFollowersCount(followersCount);

        return countVO;
    }

    /**
     * 将UserFollow转换为FollowUserVO
     *
     * @param follow        关注记录
     * @param isFollowing   true-查询关注列表（取followUserId），false-查询粉丝列表（取userId）
     * @param currentUserId 当前登录用户ID（可为 null，用于填充 followed 字段）
     */
    private FollowUserVO convertToFollowUserVO(UserFollow follow, boolean isFollowing, Long currentUserId) {
        FollowUserVO vo = new FollowUserVO();

        // 获取目标用户ID
        Long targetUserId = isFollowing ? follow.getFollowUserId() : follow.getUserId();

        try {
            User user = userService.getById(targetUserId);
            if (user != null) {
                vo.setId(user.getId());
                vo.setNickname(user.getNickname());
                vo.setAvatar(user.getAvatar());
                vo.setBio(user.getBio());
            }
        } catch (Exception e) {
            log.warn("获取用户信息失败：{}", e.getMessage());
            vo.setId(targetUserId);
        }

        vo.setFollowTime(follow.getCreateTime());

        // 填充 followed 字段：当前登录用户是否关注了该用户
        if (currentUserId != null) {
            vo.setFollowed(isFollowing(currentUserId, targetUserId));
        }

        return vo;
    }
}
