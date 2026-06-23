package com.xiaohongshu.social.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 关注用户信息VO
 */
@Data
public class FollowUserVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 用户头像
     */
    private String avatar;

    /**
     * 个人简介
     */
    private String bio;

    /**
     * 关注时间
     */
    private LocalDateTime followTime;

    /**
     * 当前登录用户是否已关注该用户（仅登录后有值）
     */
    private Boolean followed;
}
