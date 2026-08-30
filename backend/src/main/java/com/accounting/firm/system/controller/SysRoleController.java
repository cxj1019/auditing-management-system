package com.accounting.firm.system.controller;

import com.accounting.firm.common.aop.AuditLog;
import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.system.dto.RoleRequest;
import com.accounting.firm.system.entity.SysRole;
import com.accounting.firm.system.service.SysRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色管理接口
 */
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService sysRoleService;

    /** 查询全部角色 */
    @PreAuthorize("hasAuthority('system:role:list')")
    @GetMapping
    public ApiResult<List<SysRole>> list() {
        return ApiResult.success(sysRoleService.listRoles());
    }

    /** 查询角色已分配的菜单 ID 集合 */
    @PreAuthorize("hasAuthority('system:role:list')")
    @GetMapping("/{id}/menus")
    public ApiResult<List<Long>> menuIds(@PathVariable Long id) {
        return ApiResult.success(sysRoleService.getMenuIdsByRoleId(id));
    }

    /** 创建角色 */
    @AuditLog("新增角色")
    @PreAuthorize("hasAuthority('system:role:add')")
    @PostMapping
    public ApiResult<Void> create(@Valid @RequestBody RoleRequest request) {
        sysRoleService.createRole(request);
        return ApiResult.success();
    }

    /** 编辑角色 */
    @AuditLog("编辑角色")
    @PreAuthorize("hasAuthority('system:role:edit')")
    @PutMapping
    public ApiResult<Void> update(@Valid @RequestBody RoleRequest request) {
        sysRoleService.updateRole(request);
        return ApiResult.success();
    }

    /** 为角色分配菜单权限 */
    @AuditLog("分配角色权限")
    @PreAuthorize("hasAuthority('system:role:edit')")
    @PutMapping("/{id}/menus")
    public ApiResult<Void> assignMenus(@PathVariable Long id, @RequestBody List<Long> menuIds) {
        sysRoleService.assignMenus(id, menuIds);
        return ApiResult.success();
    }
}
