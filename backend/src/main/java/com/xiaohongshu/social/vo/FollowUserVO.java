package com.xiaohongshu.social.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 关注用户信息VO
 */
@Data
@Schema(description = "关注用户信息")
public class FollowUserVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "用户ID", example = "1")
    private Long id;

    @Schema(description = "用户昵称", example = "张三")
    private String nickname;

    @Schema(description = "用户头像", example = "https://example.com/avatar.jpg")
    private String avatar;

    @Schema(description = "个人简介", example = "热爱生活，分享美好")
    private String bio;

    @Schema(description = "关注时间", example = "2024-01-01 12:00:00")
    private LocalDateTime followTime;

    @Schema(description = "当前登录用户是否已关注该用户（仅登录后有值）", example = "true")
    private Boolean followed;
}
