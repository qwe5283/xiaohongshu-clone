package com.xiaohongshu.user.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户信息VO（返回给前端）
 */
@Data
public class UserVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 性别：0-未知，1-男，2-女
     */
    private Integer gender;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 个人简介
     */
    private String bio;

    /**
     * 关注数
     */
    private Long followingCount;

    /**
     * 粉丝数
     */
    private Long followersCount;

    /**
     * 获赞总数
     */
    private Long likeCount;

    /**
     * 收藏总数
     */
    private Long collectCount;

    /**
     * 获赞与收藏总数
     */
    private Long likeAndCollectCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
