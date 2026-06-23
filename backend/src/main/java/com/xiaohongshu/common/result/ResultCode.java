package com.xiaohongshu.common.result;

import lombok.Getter;

/**
 * 响应状态码枚举
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    ERROR(500, "操作失败"),

    // 用户相关 1xxx
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_PASSWORD_ERROR(1002, "密码错误"),
    USER_ALREADY_EXISTS(1003, "用户已存在"),
    USER_DISABLED(1004, "用户已被禁用"),
    USER_NOT_LOGIN(1005, "用户未登录"),
    USER_TOKEN_EXPIRED(1006, "Token已过期"),
    USER_TOKEN_INVALID(1007, "Token无效"),

    // 笔记相关 2xxx
    POST_NOT_FOUND(2001, "笔记不存在"),
    POST_ALREADY_DELETED(2002, "笔记已删除"),
    POST_NO_PERMISSION(2003, "无权操作此笔记"),

    // 评论相关 3xxx
    COMMENT_NOT_FOUND(3001, "评论不存在"),
    COMMENT_ALREADY_DELETED(3002, "评论已删除"),

    // 文件相关 4xxx
    FILE_UPLOAD_ERROR(4001, "文件上传失败"),
    FILE_NOT_FOUND(4002, "文件不存在"),
    FILE_TYPE_ERROR(4003, "文件类型不支持"),
    FILE_SIZE_ERROR(4004, "文件大小超出限制"),

    // 参数相关 5xxx
    PARAM_ERROR(5001, "参数错误"),
    PARAM_MISS(5002, "参数缺失"),

    // 关注相关 6xxx
    FOLLOW_ALREADY_EXISTS(6001, "已关注该用户"),
    FOLLOW_NOT_FOUND(6002, "未关注该用户"),
    FOLLOW_SELF(6003, "不能关注自己"),

    // 行为相关 7xxx
    ACTION_ALREADY_EXISTS(7001, "已操作过"),
    ACTION_NOT_FOUND(7002, "未操作过");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
