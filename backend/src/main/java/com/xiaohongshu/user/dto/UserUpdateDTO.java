package com.xiaohongshu.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户信息更新请求DTO
 */
@Data
@Schema(description = "用户信息更新请求")
public class UserUpdateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 昵称
     */
    @Size(max = 20, message = "昵称长度不能超过20个字符")
    @Schema(description = "昵称（最多20个字符）", example = "张三")
    private String nickname;

    /**
     * 头像URL
     */
    @Schema(description = "头像URL", example = "https://example.com/avatar.jpg")
    private String avatar;

    /**
     * 性别：0-未知，1-男，2-女
     */
    @Schema(description = "性别：0-未知，1-男，2-女", example = "1", allowableValues = {"0", "1", "2"})
    private Integer gender;

    /**
     * 邮箱
     */
    @Schema(description = "邮箱地址", example = "zhangsan@example.com")
    private String email;

    /**
     * 个人简介
     */
    @Size(max = 200, message = "个人简介长度不能超过200个字符")
    @Schema(description = "个人简介（最多200个字符）", example = "热爱生活，分享美好")
    private String bio;
}
