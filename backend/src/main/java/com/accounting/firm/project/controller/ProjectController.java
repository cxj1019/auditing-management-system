package com.accounting.firm.project.controller;

import com.accounting.firm.common.aop.AuditLog;
import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.common.api.PageResult;
import com.accounting.firm.project.dto.ProjectRequest;
import com.accounting.firm.project.entity.Project;
import com.accounting.firm.project.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 项目管理接口
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    /** 分页筛选查询项目 */
    @PreAuthorize("hasAuthority('business:project:list')")
    @GetMapping
    public ApiResult<PageResult<Project>> page(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "10") long size,
                                               @RequestParam(required = false) Integer status,
                                               @RequestParam(required = false) String type,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false)
                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                               @RequestParam(required = false)
                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResult.success(projectService.pageProjects(current, size, status, type, keyword, startDate, endDate));
    }

    /** 登记项目 */
    @AuditLog("登记项目")
    @PreAuthorize("hasAuthority('business:project:add')")
    @PostMapping
    public ApiResult<Void> create(@Valid @RequestBody ProjectRequest request) {
        projectService.createProject(request);
        return ApiResult.success();
    }

    /** 编辑项目基本信息 */
    @AuditLog("编辑项目")
    @PreAuthorize("hasAuthority('business:project:edit')")
    @PutMapping
    public ApiResult<Void> update(@Valid @RequestBody ProjectRequest request) {
        projectService.updateProject(request);
        return ApiResult.success();
    }

    /** 删除项目（仅进行中且无关联合同） */
    @AuditLog("删除项目")
    @PreAuthorize("hasAuthority('business:project:delete')")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        projectService.deleteProject(id);
        return ApiResult.success();
    }

    /** 状态流转：action=finish|reopen|archive */
    @AuditLog("项目状态流转")
    @PreAuthorize("hasAuthority('business:project:status')")
    @PutMapping("/{id}/status")
    public ApiResult<Void> changeStatus(@PathVariable Long id, @RequestParam String action) {
        projectService.changeStatus(id, action);
        return ApiResult.success();
    }
}
