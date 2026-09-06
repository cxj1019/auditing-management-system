package com.accounting.firm.cost.controller;

import com.accounting.firm.common.aop.AuditLog;
import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.common.api.PageResult;
import com.accounting.firm.cost.dto.LaborCostRequest;
import com.accounting.firm.cost.dto.OverviewVO;
import com.accounting.firm.cost.dto.ProjectProfitVO;
import com.accounting.firm.cost.entity.LaborCost;
import com.accounting.firm.cost.service.CostAnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;

/**
 * 成本分析接口
 */
@RestController
@RequestMapping("/api/cost")
@RequiredArgsConstructor
public class CostAnalysisController {

    private final CostAnalysisService costAnalysisService;

    /** 项目利润表（year=项目年份筛选） */
    @PreAuthorize("hasAuthority('business:cost:list')")
    @GetMapping("/profit")
    public ApiResult<List<ProjectProfitVO>> profit(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) Integer year) {
        return ApiResult.success(costAnalysisService.projectProfit(keyword, year));
    }

    /** 人员工时明细（项目 × 人员，按规则推算，含部门隔离），供导出 */
    @PreAuthorize("hasAuthority('business:cost:list')")
    @GetMapping("/project-hour-details")
    public ApiResult<List<java.util.Map<String, Object>>> projectHourDetails(@RequestParam(required = false) String keyword,
                                                                             @RequestParam(required = false) Integer year) {
        return ApiResult.success(costAnalysisService.projectHourDetails(keyword, year));
    }

    /** 项目工时汇总（按规则推算，含部门隔离），供导出 */
    @PreAuthorize("hasAuthority('business:cost:list')")
    @GetMapping("/project-hours")
    public ApiResult<List<java.util.Map<String, Object>>> projectHours(@RequestParam(required = false) String keyword,
                                                                       @RequestParam(required = false) Integer year) {
        return ApiResult.success(costAnalysisService.projectHours(keyword, year));
    }

    /** 经营概览 */
    @PreAuthorize("hasAuthority('business:cost:list')")
    /** 员工费用统计：已批准报销按 申请人×类别 汇总 */
    @GetMapping("/expense-stats")
    public ApiResult<List<com.accounting.firm.cost.dto.ExpenseStatVO>> expenseStats(
            @RequestParam(required = false) Integer year) {
        return ApiResult.success(costAnalysisService.expenseStats(year));
    }

    @GetMapping("/overview")
    public ApiResult<OverviewVO> overview() {
        return ApiResult.success(costAnalysisService.overview());
    }

    /** 分页查询人工成本 */
    @PreAuthorize("hasAuthority('business:cost:list')")
    @GetMapping("/labor")
    public ApiResult<PageResult<LaborCost>> laborPage(@RequestParam(defaultValue = "1") long current,
                                                      @RequestParam(defaultValue = "10") long size,
                                                      @RequestParam(required = false) Long projectId) {
        return ApiResult.success(costAnalysisService.pageLaborCosts(current, size, projectId));
    }

    /** 登记人工成本 */
    @AuditLog("登记人工成本")
    @PreAuthorize("hasAuthority('business:cost:labor-add')")
    @PostMapping("/labor")
    public ApiResult<Void> addLabor(@Valid @RequestBody LaborCostRequest request) {
        costAnalysisService.addLaborCost(request);
        return ApiResult.success();
    }

    /** 编辑人工成本 */
    @AuditLog("编辑人工成本")
    @PreAuthorize("hasAuthority('business:cost:labor-edit')")
    @PutMapping("/labor/{id}")
    public ApiResult<Void> updateLabor(@PathVariable Long id, @Valid @RequestBody LaborCostRequest request) {
        costAnalysisService.updateLaborCost(id, request);
        return ApiResult.success();
    }

    /** 删除人工成本 */
    @AuditLog("删除人工成本")
    @PreAuthorize("hasAuthority('business:cost:labor-delete')")
    @DeleteMapping("/labor/{id}")
    public ApiResult<Void> deleteLabor(@PathVariable Long id) {
        costAnalysisService.deleteLaborCost(id);
        return ApiResult.success();
    }
}
