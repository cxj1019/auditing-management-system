package com.accounting.firm.system.controller;

import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.common.api.PageResult;
import com.accounting.firm.common.aop.entity.SysAuditLog;
import com.accounting.firm.common.aop.mapper.SysAuditLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 审计日志查询接口（只读，管理员）
 */
@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final SysAuditLogMapper auditLogMapper;

    /** 分页查询审计日志 */
    @PreAuthorize("hasAuthority('system:audit:list')")
    @GetMapping
    public ApiResult<PageResult<SysAuditLog>> page(@RequestParam(defaultValue = "1") long current,
                                                   @RequestParam(defaultValue = "10") long size,
                                                   @RequestParam(required = false) String username,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false)
                                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                   @RequestParam(required = false)
                                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LambdaQueryWrapper<SysAuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(username), SysAuditLog::getUsername, username)
                .like(StringUtils.hasText(keyword), SysAuditLog::getOperation, keyword)
                .ge(startDate != null, SysAuditLog::getCreateTime, startDate == null ? null : startDate.atStartOfDay())
                .le(endDate != null, SysAuditLog::getCreateTime, endDate == null ? null : endDate.plusDays(1).atStartOfDay())
                .orderByDesc(SysAuditLog::getId);
        Page<SysAuditLog> page = auditLogMapper.selectPage(new Page<>(current, size), wrapper);
        return ApiResult.success(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }
}
