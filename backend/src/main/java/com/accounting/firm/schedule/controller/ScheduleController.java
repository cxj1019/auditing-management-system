package com.accounting.firm.schedule.controller;

import com.accounting.firm.common.aop.AuditLog;
import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.common.security.SecurityUser;
import com.accounting.firm.schedule.dto.ScheduleRequest;
import com.accounting.firm.schedule.entity.Schedule;
import com.accounting.firm.schedule.service.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    /** 按日期范围查询日程 */
    @PreAuthorize("hasAuthority('business:schedule:list')")
    @GetMapping
    public ApiResult<List<Schedule>> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long userId) {
        return ApiResult.success(scheduleService.listByDateRange(startDate, endDate, projectId, userId));
    }

    /** 创建日程 */
    @AuditLog
    @PreAuthorize("hasAuthority('business:schedule:add')")
    @PostMapping
    public ApiResult<Void> create(@Valid @RequestBody ScheduleRequest request,
                                  @AuthenticationPrincipal SecurityUser currentUser) {
        scheduleService.createSchedule(request, currentUser);
        return ApiResult.success();
    }

    /** 更新日程 */
    @AuditLog
    @PreAuthorize("hasAuthority('business:schedule:edit')")
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id,
                                  @Valid @RequestBody ScheduleRequest request,
                                  @AuthenticationPrincipal SecurityUser currentUser) {
        scheduleService.updateSchedule(id, request, currentUser);
        return ApiResult.success();
    }

    /** 删除整个日程（含全部参与人员），所有人可操作 */
    @AuditLog
    @PreAuthorize("hasAuthority('business:schedule:delete')")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        scheduleService.deleteEvent(id);
        return ApiResult.success();
    }

    /** 退出日程：仅移除当前参与人员自己的这条 */
    @AuditLog
    @PreAuthorize("hasAuthority('business:schedule:delete')")
    @DeleteMapping("/{id}/exit")
    public ApiResult<Void> exit(@PathVariable Long id) {
        scheduleService.exitEvent(id);
        return ApiResult.success();
    }

    /** 工时汇总（仅管理员/项目经理） */
    @PreAuthorize("hasAuthority('business:schedule:hours')")
    @GetMapping("/hours-summary")
    public ApiResult<List<Map<String, Object>>> hoursSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResult.success(scheduleService.hoursSummary(startDate, endDate));
    }
}
