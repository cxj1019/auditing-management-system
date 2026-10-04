package com.accounting.firm.system.controller;

import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.common.retention.RetentionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 数据保留策略：手动触发一次清理（审计日志/回收站保留期），仅管理员 */
@RestController
@RequestMapping("/api/system/retention")
@PreAuthorize("hasAuthority('system:ai:list')")
public class RetentionController {

    private final RetentionService retentionService;

    public RetentionController(RetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @PostMapping("/run")
    public ApiResult<String> run() {
        return ApiResult.success(retentionService.cleanup());
    }
}
