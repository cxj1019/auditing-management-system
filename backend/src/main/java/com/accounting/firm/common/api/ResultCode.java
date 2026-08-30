package com.accounting.firm.common.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务码枚举
 * <p>0 表示成功；4xx 客户端错误；5xx 服务端错误</p>
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    /** 成功 */
    SUCCESS(0, "操作成功"),

    /** 未认证：未登录或令牌无效/过期 */
    UNAUTHORIZED(401, "未登录或登录已过期，请重新登录"),

    /** 无权限 */
    FORBIDDEN(403, "没有操作权限"),

    /** 资源不存在 */
    NOT_FOUND(404, "资源不存在"),

    /** 参数校验失败 */
    BAD_REQUEST(400, "请求参数错误"),

    /** 业务处理失败 */
    BUSINESS_ERROR(500, "操作失败"),

    /** 系统内部错误 */
    SYSTEM_ERROR(5000, "系统繁忙，请稍后重试");

    private final int code;
    private final String message;
}
