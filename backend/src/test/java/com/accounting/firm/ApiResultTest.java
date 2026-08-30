package com.accounting.firm;

import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.common.api.ResultCode;
import com.accounting.firm.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 统一响应与业务异常单元测试（不依赖 Spring 上下文与数据库）
 */
class ApiResultTest {

    @Test
    void successShouldReturnCodeZero() {
        ApiResult<String> result = ApiResult.success("data");
        assertEquals(0, result.getCode());
        assertEquals("操作成功", result.getMessage());
        assertEquals("data", result.getData());
    }

    @Test
    void successWithoutDataShouldReturnNullData() {
        ApiResult<Void> result = ApiResult.success();
        assertEquals(0, result.getCode());
        assertNull(result.getData());
    }

    @Test
    void errorShouldCarryCodeAndMessage() {
        ApiResult<Void> result = ApiResult.error(ResultCode.FORBIDDEN);
        assertEquals(403, result.getCode());
        assertEquals("没有操作权限", result.getMessage());
    }

    @Test
    void businessExceptionShouldCarryCustomCode() {
        BusinessException e = new BusinessException(40001, "自定义业务错误");
        assertEquals(40001, e.getCode());
        assertEquals("自定义业务错误", e.getMessage());
    }
}
