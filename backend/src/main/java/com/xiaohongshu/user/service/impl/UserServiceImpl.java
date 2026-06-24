package com.xiaohongshu.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaohongshu.common.exception.BusinessException;
import com.xiaohongshu.common.result.ResultCode;
import com.xiaohongshu.post.entity.Post;
import com.xiaohongshu.post.mapper.PostMapper;
import com.xiaohongshu.social.entity.UserFollow;
import com.xiaohongshu.social.mapper.UserFollowMapper;
import com.xiaohongshu.user.dto.UserLoginDTO;
import com.xiaohongshu.user.dto.UserRegisterDTO;
import com.xiaohongshu.user.dto.UserUpdateDTO;
import com.xiaohongshu.user.entity.User;
import com.xiaohongshu.user.mapper.UserMapper;
import com.xiaohongshu.security.JwtUtil;
import com.xiaohongshu.user.service.UserService;
import com.xiaohongshu.user.vo.LoginVO;
import com.xiaohongshu.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    // 直接注入 Mapper 查社交统计，避免 UserService ↔ FollowService/PostService 循环依赖
    private final UserFollowMapper userFollowMapper;
    private final PostMapper postMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO register(UserRegisterDTO registerDTO) {
        // 检查用户名是否已存在
        User existingUser = getByUsername(registerDTO.getUsername());
        if (existingUser != null) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXISTS);
        }

        // 创建用户
        User user = new User();
        user.setUsername(registerDTO.getUsername());
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        user.setNickname(registerDTO.getNickname() != null ? registerDTO.getNickname() : registerDTO.getUsername());
        user.setPhone(registerDTO.getPhone());
        user.setAvatar("https://via.placeholder.com/100");
        user.setGender(0);
        user.setStatus(1);
        user.setDeleted(0);

        // 保存到数据库
        save(user);

        log.info("用户注册成功：{}", user.getUsername());

        // 返回用户信息
        return convertToUserVO(user);
    }

    @Override
    public LoginVO login(UserLoginDTO loginDTO) {
        // 根据用户名查询用户
        User user = getByUsername(loginDTO.getUsername());
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 验证密码
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.USER_PASSWORD_ERROR);
        }

        // 检查用户状态
        if (user.getStatus() == 0) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        // 生成Token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        // 构建登录响应
        LoginVO loginVO = LoginVO.builder()
                .token(token)
                .expiresIn(jwtUtil.getExpiration() / 1000)
                .user(convertToUserVO(user))
                .build();

        log.info("用户登录成功：{}", user.getUsername());

        return loginVO;
    }

    @Override
    public UserVO getCurrentUser(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return convertToUserVO(user);
    }

    @Override
    public UserVO getUserById(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return convertToUserVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateUser(Long userId, UserUpdateDTO updateDTO) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 更新用户信息
        if (updateDTO.getNickname() != null) {
            user.setNickname(updateDTO.getNickname());
        }
        if (updateDTO.getAvatar() != null) {
            user.setAvatar(updateDTO.getAvatar());
        }
        if (updateDTO.getGender() != null) {
            user.setGender(updateDTO.getGender());
        }
        if (updateDTO.getEmail() != null) {
            user.setEmail(updateDTO.getEmail());
        }
        if (updateDTO.getBio() != null) {
            user.setBio(updateDTO.getBio());
        }

        updateById(user);

        log.info("用户信息更新成功：{}", user.getUsername());

        return convertToUserVO(user);
    }

    @Override
    public User getByUsername(String username) {
        return getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    /**
     * 将User实体转换为UserVO，并填充社交统计字段
     */
    private UserVO convertToUserVO(User user) {
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);

        // 填充社交统计（直接查 Mapper，避免循环依赖）
        try {
            Long userId = user.getId();

            // 关注数：该用户关注了多少人
            Long followingCount = userFollowMapper.selectCount(
                new LambdaQueryWrapper<UserFollow>().eq(UserFollow::getUserId, userId)
            );
            userVO.setFollowingCount(followingCount);

            // 粉丝数：该用户被多少人关注
            Long followersCount = userFollowMapper.selectCount(
                new LambdaQueryWrapper<UserFollow>().eq(UserFollow::getFollowUserId, userId)
            );
            userVO.setFollowersCount(followersCount);

            // 获赞数与收藏数：该用户所有笔记的 like_count 和 collect_count 之和
            // Post 有 @TableLogic，selectList 会自动过滤已删除的帖子
            List<Post> userPosts = postMapper.selectList(
                new LambdaQueryWrapper<Post>()
                    .eq(Post::getUserId, userId)
                    .select(Post::getLikeCount, Post::getCollectCount)
            );
            long likeCount = userPosts.stream()
                .mapToLong(p -> p.getLikeCount() != null ? p.getLikeCount() : 0)
                .sum();
            long collectCount = userPosts.stream()
                .mapToLong(p -> p.getCollectCount() != null ? p.getCollectCount() : 0)
                .sum();
            userVO.setLikeCount(likeCount);
            userVO.setCollectCount(collectCount);
            // 保留合并字段，兼容前端已有的展示
            userVO.setLikeAndCollectCount(likeCount + collectCount);
        } catch (Exception e) {
            log.warn("获取用户社交统计失败：{}", e.getMessage());
            // 降级处理：统计字段保持 null
        }

        return userVO;
    }
}
