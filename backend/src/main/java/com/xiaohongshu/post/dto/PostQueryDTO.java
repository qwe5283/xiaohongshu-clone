package com.xiaohongshu.post.dto;

import com.xiaohongshu.common.result.PageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 笔记查询DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "笔记查询条件")
public class PostQueryDTO extends PageRequest {

    /**
     * 关键词（标题搜索）
     */
    @Schema(description = "关键词（标题搜索）", example = "美食")
    private String keyword;

    /**
     * 作者ID
     */
    @Schema(description = "作者用户ID", example = "1")
    private Long userId;

    /**
     * 类型：0-图文，1-视频
     */
    @Schema(description = "笔记类型：0-图文，1-视频", example = "0", allowableValues = {"0", "1"})
    private Integer type;

    /**
     * 状态
     */
    @Schema(description = "状态", example = "1")
    private Integer status;

    /**
     * 排序方式：latest（最新）/ hot（最热）
     */
    @Schema(description = "排序方式：latest（最新）/ hot（最热）", example = "latest", allowableValues = {"latest", "hot"})
    private String sortType = "latest";
}
