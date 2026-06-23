package com.xiaohongshu.post.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 笔记图片VO
 */
@Data
public class PostImageVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 图片ID
     */
    private Long id;

    /**
     * 图片URL
     */
    private String imageUrl;

    /**
     * 排序顺序
     */
    private Integer sortOrder;

    /**
     * 图片宽度
     */
    private Integer width;

    /**
     * 图片高度
     */
    private Integer height;
}
