package com.xiaohongshu.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xiaohongshu.user.dto.UserLoginDTO;
import com.xiaohongshu.user.dto.UserRegisterDTO;
import com.xiaohongshu.user.dto.UserUpdateDTO;
import com.xiaohongshu.user.entity.User;
import com.xiaohongshu.user.vo.LoginVO;
import com.xiaohongshu.user.vo.UserVO;

/**
 * 用户服务接口
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param registerDTO 注册请求DTO
     * @return 用户信息
     */
    UserVO register(UserRegisterDTO registerDTO);

    /**
     * 用户登录
     *
     * @param loginDTO 登录请求DTO
     * @return 登录响应（包含Token）
     */
    LoginVO login(UserLoginDTO loginDTO);

    /**
     * 获取当前登录用户信息
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    UserVO getCurrentUser(Long userId);

    /**
     * 根据用户ID获取用户信息
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    UserVO getUserById(Long userId);

    /**
     * 更新用户信息
     *
     * @param userId      用户ID
     * @param updateDTO   更新请求DTO
     * @return 更新后的用户信息
     */
    UserVO updateUser(Long userId, UserUpdateDTO updateDTO);

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户实体
     */
    User getByUsername(String username);
}
