package com.accounting.firm.system.controller;

import com.accounting.firm.common.aop.AuditLog;
import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.system.entity.SysMenu;
import com.accounting.firm.system.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 菜单管理接口（树形结构）
 */
@RestController
@RequestMapping("/api/menus")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService sysMenuService;

    /** 查询全部菜单树（含按钮权限点） */
    @PreAuthorize("hasAuthority('system:menu:list')")
    @GetMapping
    public ApiResult<List<SysMenu>> tree() {
        return ApiResult.success(sysMenuService.getMenuTree());
    }

    /** 创建菜单/按钮 */
    @AuditLog("新增菜单")
    @PreAuthorize("hasAuthority('system:menu:add')")
    @PostMapping
    public ApiResult<Void> create(@RequestBody SysMenu menu) {
        sysMenuService.createMenu(menu);
        return ApiResult.success();
    }

    /** 编辑菜单/按钮 */
    @AuditLog("编辑菜单")
    @PreAuthorize("hasAuthority('system:menu:edit')")
    @PutMapping
    public ApiResult<Void> update(@RequestBody SysMenu menu) {
        sysMenuService.updateMenu(menu);
        return ApiResult.success();
    }
}
