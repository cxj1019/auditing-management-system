package com.accounting.firm.system.controller;

import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.system.entity.SysDepartment;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accounting.firm.system.mapper.SysDepartmentMapper;

import java.util.List;

/**
 * 部门管理接口
 */
@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class SysDepartmentController {

    private final SysDepartmentMapper deptMapper;

    /** 部门清单（任意已认证用户可访问，供下拉选择） */
    @GetMapping("/options")
    public ApiResult<List<SysDepartment>> options() {
        return ApiResult.success(deptMapper.selectList(
                new LambdaQueryWrapper<SysDepartment>().orderByAsc(SysDepartment::getSort)));
    }

    /** 部门列表 */
    @PreAuthorize("hasAuthority('system:dept:list')")
    @GetMapping
    public ApiResult<List<SysDepartment>> list() {
        return ApiResult.success(deptMapper.selectList(
                new LambdaQueryWrapper<SysDepartment>().orderByAsc(SysDepartment::getSort)));
    }

    /** 创建部门 */
    @PreAuthorize("hasAuthority('system:dept:add')")
    @PostMapping
    public ApiResult<Void> create(@RequestBody SysDepartment dept) {
        deptMapper.insert(dept);
        return ApiResult.success();
    }

    /** 编辑部门 */
    @PreAuthorize("hasAuthority('system:dept:edit')")
    @PutMapping
    public ApiResult<Void> update(@RequestBody SysDepartment dept) {
        deptMapper.updateById(dept);
        return ApiResult.success();
    }

    /** 删除部门 */
    @PreAuthorize("hasAuthority('system:dept:delete')")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        deptMapper.deleteById(id);
        return ApiResult.success();
    }
}
