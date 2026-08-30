package com.accounting.firm.system.controller;

import com.accounting.firm.common.aop.AuditLog;
import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.common.api.PageResult;
import com.accounting.firm.system.dto.UserOptionVO;
import com.accounting.firm.system.dto.UserRequest;
import com.accounting.firm.system.dto.UserVO;
import com.accounting.firm.system.service.SysUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户管理接口
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;

    /** 分页查询用户 */
    @PreAuthorize("hasAuthority('system:user:list')")
    @GetMapping
    public ApiResult<PageResult<UserVO>> page(@RequestParam(defaultValue = "1") long current,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) String keyword) {
        return ApiResult.success(sysUserService.pageUsers(current, size, keyword));
    }

    /** 在册人员选项（启用状态用户，供人员下拉选择；任意已认证用户可访问） */
    @GetMapping("/options")
    public ApiResult<List<UserOptionVO>> options() {
        return ApiResult.success(sysUserService.listOptions());
    }

    /** 创建用户 */
    @AuditLog("新增用户")
    @PreAuthorize("hasAuthority('system:user:add')")
    @PostMapping
    public ApiResult<Void> create(@Valid @RequestBody UserRequest request) {
        sysUserService.createUser(request);
        return ApiResult.success();
    }

    /** 编辑用户 */
    @AuditLog("编辑用户")
    @PreAuthorize("hasAuthority('system:user:edit')")
    @PutMapping
    public ApiResult<Void> update(@Valid @RequestBody UserRequest request) {
        sysUserService.updateUser(request);
        return ApiResult.success();
    }
}
