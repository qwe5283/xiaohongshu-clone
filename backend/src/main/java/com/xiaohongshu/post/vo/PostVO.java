package com.xiaohongshu.post.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 笔记信息VO（返回给前端）
 */
@Data
@Schema(description = "笔记信息")
public class PostVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "笔记ID", example = "1")
    private Long id;

    @Schema(description = "作者用户ID", example = "1")
    private Long userId;

    @Schema(description = "作者昵称", example = "张三")
    private String authorNickname;

    @Schema(description = "作者头像", example = "https://example.com/avatar.jpg")
    private String authorAvatar;

    @Schema(description = "标题", example = "我的第一篇文章")
    private String title;

    @Schema(description = "正文内容", example = "这是一篇分享笔记...")
    private String content;

    @Schema(description = "类型：0-图文，1-视频", example = "0", allowableValues = {"0", "1"})
    private Integer type;

    @Schema(description = "封面图URL", example = "https://example.com/cover.jpg")
    private String coverImage;

    @Schema(description = "视频URL", example = "https://example.com/video.mp4")
    private String videoUrl;

    @Schema(description = "图片列表")
    private List<PostImageVO> images;

    @Schema(description = "浏览量", example = "1000")
    private Integer viewCount;

    @Schema(description = "点赞数", example = "50")
    private Integer likeCount;

    @Schema(description = "评论数", example = "10")
    private Integer commentCount;

    @Schema(description = "收藏数", example = "20")
    private Integer collectCount;

    @Schema(description = "当前登录用户是否已点赞（未登录时为false）", example = "true")
    private Boolean liked;

    @Schema(description = "状态：0-草稿，1-已发布", example = "1")
    private Integer status;

    @Schema(description = "创建时间", example = "2024-01-01 12:00:00")
    private LocalDateTime createTime;

    @Schema(description = "更新时间", example = "2024-01-01 12:00:00")
    private LocalDateTime updateTime;
}
