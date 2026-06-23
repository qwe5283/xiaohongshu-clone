package com.xiaohongshu.post.dto;

import com.xiaohongshu.common.result.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 笔记查询DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PostQueryDTO extends PageRequest {

    /**
     * 关键词（标题搜索）
     */
    private String keyword;

    /**
     * 作者ID
     */
    private Long userId;

    /**
     * 类型：0-图文，1-视频
     */
    private Integer type;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 排序方式：latest（最新）/ hot（最热）
     */
    private String sortType = "latest";
}
