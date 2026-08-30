package com.accounting.firm.common.aop;

/**
 * 审计日志操作人提取接口
 * <p>未认证上下文（如登录接口）中，请求参数对象实现本接口后，
 * 审计切面可从中提取操作账号进行记录。</p>
 */
public interface UsernameProvider {

    /** 返回操作账号 */
    String getUsername();
}
