package com.xiaohongshu.social.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 关注/粉丝数量VO
 */
@Data
@Schema(description = "关注粉丝数量")
public class FollowCountVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "关注数", example = "10")
    private Long followingCount;

    @Schema(description = "粉丝数", example = "100")
    private Long followersCount;
}
