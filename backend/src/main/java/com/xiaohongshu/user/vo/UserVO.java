package com.xiaohongshu.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户信息VO（返回给前端）
 */
@Data
@Schema(description = "用户信息")
public class UserVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "用户ID", example = "1")
    private Long id;

    @Schema(description = "用户名", example = "zhangsan")
    private String username;

    @Schema(description = "昵称", example = "张三")
    private String nickname;

    @Schema(description = "头像URL", example = "https://example.com/avatar.jpg")
    private String avatar;

    @Schema(description = "性别：0-未知，1-男，2-女", example = "1", allowableValues = {"0", "1", "2"})
    private Integer gender;

    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @Schema(description = "邮箱", example = "zhangsan@example.com")
    private String email;

    @Schema(description = "个人简介", example = "热爱生活，分享美好")
    private String bio;

    @Schema(description = "关注数", example = "10")
    private Long followingCount;

    @Schema(description = "粉丝数", example = "100")
    private Long followersCount;

    @Schema(description = "获赞总数", example = "500")
    private Long likeCount;

    @Schema(description = "收藏总数", example = "200")
    private Long collectCount;

    @Schema(description = "获赞与收藏总数", example = "700")
    private Long likeAndCollectCount;

    @Schema(description = "注册时间", example = "2024-01-01 12:00:00")
    private LocalDateTime createTime;
}
