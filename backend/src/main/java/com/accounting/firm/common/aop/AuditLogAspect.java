package com.accounting.firm.common.aop;

import com.accounting.firm.common.aop.entity.SysAuditLog;
import com.accounting.firm.common.aop.mapper.SysAuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 审计日志切面
 * <p>记录操作人、操作时间、操作内容与结果：输出 SLF4J 日志并落库 sys_audit_log，
 * 落库失败不影响业务（仅记录告警）。</p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final SysAuditLogMapper auditLogMapper;

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        String operator = resolveOperator(joinPoint);
        String operation = auditLog.value();
        LocalDateTime time = LocalDateTime.now();
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long cost = System.currentTimeMillis() - start;
            log.info("[审计] 操作人={}, 时间={}, 操作={}, 结果=成功, 耗时={}ms, IP={}",
                    operator, time, operation, cost, clientIp());
            persist(operator, operation, "成功", null, cost, time);
            return result;
        } catch (Throwable e) {
            long cost = System.currentTimeMillis() - start;
            log.info("[审计] 操作人={}, 时间={}, 操作={}, 结果=失败({}), 耗时={}ms, IP={}",
                    operator, time, operation, e.getMessage(), cost, clientIp());
            persist(operator, operation, "失败", e.getMessage(), cost, time);
            throw e;
        }
    }

    /** 落库失败仅告警，不影响业务 */
    private void persist(String operator, String operation, String result, String errorMsg, long cost, LocalDateTime time) {
        try {
            SysAuditLog record = new SysAuditLog();
            record.setUsername(operator);
            record.setOperation(operation);
            record.setResult(result);
            record.setErrorMsg(errorMsg != null && errorMsg.length() > 290
                    ? errorMsg.substring(0, 290) : errorMsg);
            record.setCostMs(cost);
            record.setIp(clientIp());
            record.setCreateTime(time);
            auditLogMapper.insert(record);
        } catch (Exception e) {
            log.warn("[审计] 日志落库失败: {}", e.getMessage());
        }
    }

    /**
     * 解析操作人：优先取已认证用户；未认证上下文（如登录接口）
     * 从实现了 UsernameProvider 的请求参数中提取账号
     */
    private String resolveOperator(ProceedingJoinPoint joinPoint) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof UsernameProvider provider && provider.getUsername() != null) {
                return provider.getUsername();
            }
        }
        return "anonymous";
    }

    /** 获取客户端 IP */
    private String clientIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                return attrs.getRequest().getRemoteAddr();
            }
        } catch (Exception ignored) {
            // 非 Web 上下文
        }
        return "unknown";
    }
}
