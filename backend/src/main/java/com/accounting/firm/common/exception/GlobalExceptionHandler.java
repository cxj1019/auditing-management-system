package com.accounting.firm.common.exception;

import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.common.api.ResultCode;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器
 * <p>统一将各类异常转换为 ApiResult 错误响应；未预期异常不向客户端泄露堆栈信息</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：返回对应业务码与消息 */
    @ExceptionHandler(BusinessException.class)
    public ApiResult<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return ApiResult.error(e.getCode(), e.getMessage());
    }

    /** 无权限访问 */
    @ExceptionHandler(AccessDeniedException.class)
    public ApiResult<Void> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("权限拒绝: {}", e.getMessage());
        return ApiResult.error(ResultCode.FORBIDDEN);
    }

    /** 请求体参数校验失败（@Valid） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResult<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "请求参数错误";
        return ApiResult.error(ResultCode.BAD_REQUEST, message);
    }

    /** 表单绑定校验失败 */
    @ExceptionHandler(BindException.class)
    public ApiResult<Void> handleBindException(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "请求参数错误";
        return ApiResult.error(ResultCode.BAD_REQUEST, message);
    }

    /** 单参数校验失败（@Validated 方法参数） */
    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResult<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getMessage())
                .orElse("请求参数错误");
        return ApiResult.error(ResultCode.BAD_REQUEST, message);
    }

    /** 请求体不可读 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResult<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        return ApiResult.error(ResultCode.BAD_REQUEST, "请求体格式错误");
    }

    /** 静态资源/路径不存在 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ApiResult<Void> handleNoResourceFoundException(NoResourceFoundException e) {
        return ApiResult.error(ResultCode.NOT_FOUND);
    }

    /** 未预期异常：只返回通用错误，不泄露堆栈与内部信息 */
    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleException(Exception e) {
        log.error("系统未预期异常", e);
        return ApiResult.error(ResultCode.SYSTEM_ERROR);
    }
}
