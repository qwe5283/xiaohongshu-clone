package com.xiaohongshu.user.controller;

import com.xiaohongshu.common.result.Result;
import com.xiaohongshu.user.dto.UserLoginDTO;
import com.xiaohongshu.user.dto.UserRegisterDTO;
import com.xiaohongshu.user.dto.UserUpdateDTO;
import com.xiaohongshu.security.JwtUtil;
import com.xiaohongshu.user.service.UserService;
import com.xiaohongshu.user.vo.LoginVO;
import com.xiaohongshu.user.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户控制器
 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    /**
     * 用户注册
     */
    @Operation(summary = "用户注册", description = "创建新用户账号，用户名长度3-20字符，密码长度6-20字符")
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody UserRegisterDTO registerDTO) {
        UserVO userVO = userService.register(registerDTO);
        return Result.success("注册成功", userVO);
    }

    /**
     * 用户登录
     */
    @Operation(summary = "用户登录", description = "使用用户名和密码登录，返回JWT令牌")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody UserLoginDTO loginDTO) {
        LoginVO loginVO = userService.login(loginDTO);
        return Result.success("登录成功", loginVO);
    }

    /**
     * 获取当前登录用户信息
     */
    @Operation(summary = "获取当前用户信息", description = "根据请求头中的JWT令牌返回当前登录用户的信息")
    @GetMapping("/me")
    public Result<UserVO> getCurrentUser(
            @Parameter(description = "JWT认证令牌（Bearer Token）", required = true)
            @RequestHeader(value = "Authorization", required = false) String token) {
        Long userId = jwtUtil.getUserIdFromToken(token);
        UserVO userVO = userService.getCurrentUser(userId);
        return Result.success(userVO);
    }

    /**
     * 根据ID获取用户信息
     */
    @Operation(summary = "根据ID获取用户信息", description = "通过用户ID查询用户的公开信息")
    @GetMapping("/{id}")
    public Result<UserVO> getUserById(
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable Long id) {
        UserVO userVO = userService.getUserById(id);
        return Result.success(userVO);
    }

    /**
     * 更新用户信息
     */
    @Operation(summary = "更新用户信息", description = "登录用户修改自己的个人信息，包括昵称、头像、性别、邮箱、简介等")
    @PutMapping("/update")
    public Result<UserVO> updateUser(
            @Parameter(description = "JWT认证令牌（Bearer Token）", required = true)
            @RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody UserUpdateDTO updateDTO) {
        Long userId = jwtUtil.getUserIdFromToken(token);
        UserVO userVO = userService.updateUser(userId, updateDTO);
        return Result.success("更新成功", userVO);
    }
}
