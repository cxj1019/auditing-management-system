package com.accounting.firm.common.api;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一 API 响应结构
 * <p>所有接口返回统一格式：code（业务码）、message（消息）、data（数据）</p>
 *
 * @param <T> 数据类型
 */
@Data
public class ApiResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务码：0 表示成功，非 0 表示失败 */
    private int code;

    /** 消息 */
    private String message;

    /** 数据 */
    private T data;

    private ApiResult() {
    }

    private ApiResult(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 成功（无数据） */
    public static <T> ApiResult<T> success() {
        return new ApiResult<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    /** 成功（带数据） */
    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /** 失败（指定业务码与消息） */
    public static <T> ApiResult<T> error(int code, String message) {
        return new ApiResult<>(code, message, null);
    }

    /** 失败（使用 ResultCode 枚举） */
    public static <T> ApiResult<T> error(ResultCode resultCode) {
        return new ApiResult<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /** 失败（使用 ResultCode 枚举 + 自定义消息） */
    public static <T> ApiResult<T> error(ResultCode resultCode, String message) {
        return new ApiResult<>(resultCode.getCode(), message, null);
    }
}
