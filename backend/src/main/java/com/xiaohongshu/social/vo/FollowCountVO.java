package com.xiaohongshu.social.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 关注/粉丝数量VO
 */
@Data
public class FollowCountVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 关注数
     */
    private Long followingCount;

    /**
     * 粉丝数
     */
    private Long followersCount;
}
