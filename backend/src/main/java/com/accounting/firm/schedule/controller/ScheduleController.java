package com.accounting.firm.schedule.controller;

import com.accounting.firm.common.aop.AuditLog;
import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.common.api.ResultCode;
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

    /** 可选设备清单（会议室/公司车辆等），所有能看日程的人可用 */
    @GetMapping("/resources")
    public ApiResult<List<com.accounting.firm.schedule.entity.ScheduleResource>> resources() {
        return ApiResult.success(scheduleService.listResources());
    }

    /** 人 × 项目 工时矩阵（部门范围内） */
    @PreAuthorize("hasAuthority('business:schedule:hours')")
    @GetMapping("/hours-matrix")
    public ApiResult<List<java.util.Map<String, Object>>> hoursMatrix(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResult.success(scheduleService.hoursMatrix(startDate, endDate));
    }

    /** 经理确认成员时段工时 */
    @PreAuthorize("hasAuthority('business:schedule:hours')")
    @PostMapping("/confirm")
    public ApiResult<Integer> confirm(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                      @RequestParam(required = false) Long userId,
                                      @AuthenticationPrincipal SecurityUser currentUser) {
        return ApiResult.success(scheduleService.confirmHours(startDate, endDate, userId, currentUser));
    }

    /** 已锁定月份清单 */
    @GetMapping("/locks")
    public ApiResult<List<String>> locks() {
        return ApiResult.success(scheduleService.listLocks());
    }

    /** 锁定/解锁月份（仅管理员） */
    @PostMapping("/locks")
    public ApiResult<Void> lockMonth(@RequestParam String month,
                                     @AuthenticationPrincipal SecurityUser currentUser) {
        if (!currentUser.hasRole("admin")) {
            return ApiResult.error(ResultCode.FORBIDDEN);
        }
        scheduleService.lockMonth(month, currentUser);
        return ApiResult.success();
    }

    @DeleteMapping("/locks")
    public ApiResult<Void> unlockMonth(@RequestParam String month,
                                       @AuthenticationPrincipal SecurityUser currentUser) {
        if (!currentUser.hasRole("admin")) {
            return ApiResult.error(ResultCode.FORBIDDEN);
        }
        scheduleService.unlockMonth(month, currentUser);
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
