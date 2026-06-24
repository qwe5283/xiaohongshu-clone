package com.xiaohongshu.post.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 笔记信息VO（返回给前端）
 */
@Data
public class PostVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 笔记ID
     */
    private Long id;

    /**
     * 作者ID
     */
    private Long userId;

    /**
     * 作者昵称
     */
    private String authorNickname;

    /**
     * 作者头像
     */
    private String authorAvatar;

    /**
     * 标题
     */
    private String title;

    /**
     * 正文内容
     */
    private String content;

    /**
     * 类型：0-图文，1-视频
     */
    private Integer type;

    /**
     * 封面图URL
     */
    private String coverImage;

    /**
     * 视频URL
     */
    private String videoUrl;

    /**
     * 图片列表
     */
    private List<PostImageVO> images;

    /**
     * 浏览量
     */
    private Integer viewCount;

    /**
     * 点赞数
     */
    private Integer likeCount;

    /**
     * 评论数
     */
    private Integer commentCount;

    /**
     * 收藏数
     */
    private Integer collectCount;

    /**
     * 当前登录用户是否已点赞（未登录时为false）
     */
    private Boolean liked;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
