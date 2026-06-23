package com.xiaohongshu.common.exception;

import com.xiaohongshu.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常
 * 携带 ResultCode，使全局异常处理器能返回正确的业务错误码
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ResultCode resultCode;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }
}
