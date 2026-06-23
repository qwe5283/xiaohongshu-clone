package com.xiaohongshu.interact.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户行为实体类（点赞/收藏）
 */
@Data
@TableName("user_action")
public class UserAction implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 行为ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 目标ID（笔记ID或评论ID）
     */
    private Long targetId;

    /**
     * 目标类型：1-笔记，2-评论
     */
    private Integer targetType;

    /**
     * 行为类型：1-点赞，2-收藏
     */
    private Integer actionType;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
