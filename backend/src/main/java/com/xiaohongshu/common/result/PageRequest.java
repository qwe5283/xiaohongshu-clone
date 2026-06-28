package com.xiaohongshu.common.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 分页请求参数
 */
@Data
@Schema(description = "分页请求参数")
public class PageRequest {

    /**
     * 页码（从1开始）
     */
    @Min(value = 1, message = "页码最小为1")
    @Schema(description = "页码（从1开始）", example = "1")
    private Integer pageNum = 1;

    /**
     * 每页数量
     */
    @Min(value = 1, message = "每页数量最小为1")
    @Max(value = 100, message = "每页数量最大为100")
    @Schema(description = "每页数量（1-100）", example = "10")
    private Integer pageSize = 10;

    /**
     * 排序字段
     */
    @Schema(description = "排序字段", example = "create_time")
    private String orderBy;

    /**
     * 排序方式：asc/desc
     */
    @Schema(description = "排序方式：asc（升序）/ desc（降序）", example = "desc", allowableValues = {"asc", "desc"})
    private String orderDirection = "desc";

    /**
     * 获取页码（null安全，保证不返回null）
     */
    public int getPageNumSafe() {
        return pageNum != null ? pageNum : 1;
    }

    /**
     * 获取每页数量（null安全，保证不返回null）
     */
    public int getPageSizeSafe() {
        return pageSize != null ? pageSize : 10;
    }
}
