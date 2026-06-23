package com.xiaohongshu.user.controller;

import com.xiaohongshu.common.result.Result;
import com.xiaohongshu.user.dto.UserLoginDTO;
import com.xiaohongshu.user.dto.UserRegisterDTO;
import com.xiaohongshu.user.dto.UserUpdateDTO;
import com.xiaohongshu.security.JwtUtil;
import com.xiaohongshu.user.service.UserService;
import com.xiaohongshu.user.vo.LoginVO;
import com.xiaohongshu.user.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody UserRegisterDTO registerDTO) {
        UserVO userVO = userService.register(registerDTO);
        return Result.success("注册成功", userVO);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody UserLoginDTO loginDTO) {
        LoginVO loginVO = userService.login(loginDTO);
        return Result.success("登录成功", loginVO);
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/me")
    public Result<UserVO> getCurrentUser(@RequestHeader(value = "Authorization", required = false) String token) {
        Long userId = jwtUtil.getUserIdFromToken(token);
        UserVO userVO = userService.getCurrentUser(userId);
        return Result.success(userVO);
    }

    /**
     * 根据ID获取用户信息
     */
    @GetMapping("/{id}")
    public Result<UserVO> getUserById(@PathVariable Long id) {
        UserVO userVO = userService.getUserById(id);
        return Result.success(userVO);
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/update")
    public Result<UserVO> updateUser(
            @RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody UserUpdateDTO updateDTO) {
        Long userId = jwtUtil.getUserIdFromToken(token);
        UserVO userVO = userService.updateUser(userId, updateDTO);
        return Result.success("更新成功", userVO);
    }
}
