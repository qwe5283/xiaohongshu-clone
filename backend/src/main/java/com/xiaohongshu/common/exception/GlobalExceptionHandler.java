package com.xiaohongshu.common.exception;

import com.xiaohongshu.common.result.Result;
import com.xiaohongshu.common.result.ResultCode;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理JWT相关异常（Token无效/缺失/过期/格式错误）
     * 返回401 Unauthorized
     */
    @ExceptionHandler(JwtException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleJwtException(JwtException e) {
        log.warn("JWT认证异常：{}", e.getMessage());
        return Result.error(ResultCode.USER_NOT_LOGIN);
    }

    /**
     * 处理Token为空/无效导致的IllegalArgumentException
     * JwtUtil在token为空时会抛出此异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        String msg = e.getMessage();
        // Token相关：返回401
        if (msg != null && (msg.contains("Token") || msg.contains("token")
                || msg.contains("JWT") || msg.contains("jwt")
                || msg.contains("CharSequence") || msg.contains("null or empty"))) {
            log.warn("认证异常：{}", msg);
            return Result.error(ResultCode.USER_NOT_LOGIN);
        }
        // 非认证相关的参数异常：清理消息，不泄漏框架内部信息
        log.warn("参数异常：{}", msg);
        return Result.error(ResultCode.PARAM_ERROR.getCode(), "参数错误");
    }

    /**
     * 处理路径参数类型不匹配异常（如 /api/user/abc 中abc无法转为Long）
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配：{}", e.getName());
        return Result.error(ResultCode.PARAM_ERROR.getCode(), "参数类型不正确: " + e.getName());
    }

    /**
     * 处理请求体不可读异常（空Body、无效JSON等）
     * 返回400，不泄漏框架内部信息
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败：{}", e.getMessage());
        return Result.error(ResultCode.PARAM_ERROR.getCode(), "请求体格式不正确或为空");
    }

    /**
     * 处理HTTP方法不支持异常
     * 返回405
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("不支持的请求方法：{}", e.getMethod());
        return Result.error(ResultCode.PARAM_ERROR.getCode(), "不支持的请求方法: " + e.getMethod());
    }

    /**
     * 处理缺少请求参数异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数：{}", e.getParameterName());
        return Result.error(ResultCode.PARAM_MISS.getCode(), "缺少参数: " + e.getParameterName());
    }

    /**
     * 处理缺少multipart文件异常
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingServletRequestPartException(MissingServletRequestPartException e) {
        log.warn("缺少文件：{}", e.getRequestPartName());
        return Result.error(ResultCode.PARAM_MISS.getCode(), "缺少文件: " + e.getRequestPartName());
    }

    /**
     * 处理资源不存在异常（Spring 6.x / Boot 3.2+ 无匹配路由时抛出）
     * 如 GET /user/ (尾斜杠)、GET /不存在的路径
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("资源不存在：{}", e.getResourcePath());
        return Result.error(ResultCode.PARAM_ERROR.getCode(), "请求的资源不存在");
    }

    /**
     * 处理Content-Type不支持异常
     * 如 POST 请求用 text/xml 而非 application/json
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public Result<Void> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e) {
        log.warn("不支持的媒体类型：{}", e.getContentType());
        return Result.error(ResultCode.PARAM_ERROR.getCode(), "不支持的Content-Type: " + e.getContentType());
    }

    /**
     * 处理业务异常（携带错误码）
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常：code={}, message={}", e.getResultCode().getCode(), e.getMessage());
        return Result.error(e.getResultCode().getCode(), e.getMessage());
    }

    /**
     * 处理其他运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常：{}", e.getMessage());
        return Result.error(e.getMessage());
    }

    /**
     * 处理参数校验异常（@RequestBody @Valid 校验失败）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> {
                    String field = fe.getField();
                    String msg = fe.getDefaultMessage();
                    // 清理消息，不泄漏框架内部信息
                    if (msg != null && (msg.contains("java.lang") || msg.contains("Failed to convert"))) {
                        return "参数类型不正确: " + field;
                    }
                    return msg != null ? msg : "参数错误: " + field;
                })
                .collect(Collectors.joining(", "));
        log.warn("参数校验异常：{}", message);
        return Result.error(ResultCode.PARAM_ERROR.getCode(), message);
    }

    /**
     * 处理绑定异常（查询参数类型不匹配等）
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(fe -> {
                    // 清理消息，不泄漏框架内部信息
                    String field = fe.getField();
                    String msg = fe.getDefaultMessage();
                    if (msg != null && (msg.contains("java.lang") || msg.contains("Failed to convert"))) {
                        return "参数类型不正确: " + field;
                    }
                    return msg != null ? msg : "参数错误: " + field;
                })
                .collect(Collectors.joining(", "));
        log.warn("绑定异常：{}", message);
        return Result.error(ResultCode.PARAM_ERROR.getCode(), message);
    }

    /**
     * 处理其他异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常：{}", e.getMessage(), e);
        return Result.error("系统内部错误，请稍后重试");
    }
}
