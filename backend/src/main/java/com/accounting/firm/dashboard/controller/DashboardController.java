package com.accounting.firm.dashboard.controller;

import com.accounting.firm.collection.entity.ContractPayment;
import com.accounting.firm.collection.mapper.ContractPaymentMapper;
import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.common.security.DataScopeService;
import com.accounting.firm.common.security.SecurityUser;
import com.accounting.firm.confirmation.entity.Confirmation;
import com.accounting.firm.confirmation.mapper.ConfirmationMapper;
import com.accounting.firm.contract.entity.Contract;
import com.accounting.firm.contract.entity.ContractStatus;
import com.accounting.firm.contract.mapper.ContractMapper;
import com.accounting.firm.cost.dto.ProjectProfitVO;
import com.accounting.firm.cost.mapper.CostAnalysisMapper;
import com.accounting.firm.dashboard.dto.DashboardVO;
import com.accounting.firm.invoice.entity.Invoice;
import com.accounting.firm.invoice.entity.InvoiceStatus;
import com.accounting.firm.invoice.mapper.InvoiceMapper;
import com.accounting.firm.schedule.service.ScheduleHoursCalculator;
import com.accounting.firm.project.entity.Project;
import com.accounting.firm.project.mapper.ProjectMapper;
import com.accounting.firm.reimbursement.entity.Reimbursement;
import com.accounting.firm.reimbursement.mapper.ReimbursementMapper;
import com.accounting.firm.schedule.entity.Schedule;
import com.accounting.firm.schedule.mapper.ScheduleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工作台聚合接口
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ReimbursementMapper reimbursementMapper;
    private final InvoiceMapper invoiceMapper;
    private final ContractPaymentMapper paymentMapper;
    private final ConfirmationMapper confirmationMapper;
    private final ContractMapper contractMapper;
    private final ScheduleMapper scheduleMapper;
    private final ProjectMapper projectMapper;
    private final CostAnalysisMapper costAnalysisMapper;
    private final DataScopeService dataScopeService;

    @GetMapping
    public ApiResult<DashboardVO> dashboard(@AuthenticationPrincipal SecurityUser currentUser) {
        DashboardVO vo = new DashboardVO();
        LocalDate today = LocalDate.now();
        var scope = dataScopeService.currentScope();

        // ---------- 待办计数 ----------
        DashboardVO.Todo todo = new DashboardVO.Todo();
        todo.setPendingReimbursement(countReimbursementPending(scope));
        todo.setPendingInvoice(countPendingInvoices(scope));
        todo.setOverdueReceivable(countOverdueReceivables(today));
        todo.setOverdueConfirmation(countOverdueConfirmations(today, scope));
        todo.setExpiringContract(countExpiringContracts(today, scope));
        vo.setTodo(todo);

        // 普通员工不展示工时与成本/经营数据
        boolean canViewFinance = currentUser.hasRole("admin") || currentUser.hasRole("manager");
        if (!canViewFinance) {
            return ApiResult.success(vo);
        }

        // ---------- 本周工时 + 今日日程（当前用户） ----------
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);
        List<Schedule> weekSchedules = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getUserId, currentUser.getUserId())
                .ge(Schedule::getScheduleDate, monday)
                .le(Schedule::getScheduleDate, sunday));
        vo.setWeekHours(weekSchedules.stream()
                .map(ScheduleHoursCalculator::effectiveHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        List<Schedule> todaySchedules = weekSchedules.stream()
                .filter(s -> !s.getScheduleDate().isBefore(today)
                        && (s.getEndDate() == null || !s.getEndDate().isBefore(today)))
                .sorted(Comparator.comparing(s -> s.getStartTime() == null ? "" : s.getStartTime()))
                .toList();
        List<Long> projectIds = todaySchedules.stream()
                .map(Schedule::getProjectId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, Project> projectMap = projectIds.isEmpty() ? Map.of()
                : projectMapper.selectBatchIds(projectIds).stream()
                        .collect(Collectors.toMap(Project::getId, p -> p));
        vo.setTodaySchedules(todaySchedules.stream().map(s -> {
            DashboardVO.ScheduleItem item = new DashboardVO.ScheduleItem();
            item.setId(s.getId());
            item.setTitle(s.getTitle());
            item.setType(s.getType());
            item.setStartTime(s.getStartTime());
            item.setEndTime(s.getEndTime());
            item.setHours(s.getHours());
            Project project = projectMap.get(s.getProjectId());
            item.setProjectName(project == null ? null : project.getName());
            return item;
        }).toList());

        // ---------- 开票与回款总览（已开票未作废） ----------
        List<Invoice> issued = invoiceMapper.selectList(new LambdaQueryWrapper<Invoice>()
                .eq(Invoice::getStatus, InvoiceStatus.ISSUED.getCode()));
        List<Long> invoiceIds = issued.stream().map(Invoice::getId).toList();
        BigDecimal collected = invoiceIds.isEmpty() ? BigDecimal.ZERO
                : paymentMapper.selectList(new LambdaQueryWrapper<ContractPayment>()
                        .in(ContractPayment::getInvoiceId, invoiceIds))
                        .stream().map(p -> p.getAmount() == null ? BigDecimal.ZERO : p.getAmount())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal invoiced = issued.stream()
                .map(i -> i.getAmount() == null ? BigDecimal.ZERO : i.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        DashboardVO.Receivable receivable = new DashboardVO.Receivable();
        receivable.setInvoicedAmount(invoiced);
        receivable.setCollectedAmount(collected);
        receivable.setOutstanding(invoiced.subtract(collected));
        vo.setReceivable(receivable);

        // ---------- 项目规模 Top5 ----------
        List<ProjectProfitVO> profits = costAnalysisMapper.selectProjectProfit(null, null, null, null);
        List<ProjectProfitVO> top = profits.stream()
                .sorted((a, b) -> (b.getContractAmount() == null ? BigDecimal.ZERO : b.getContractAmount())
                        .compareTo(a.getContractAmount() == null ? BigDecimal.ZERO : a.getContractAmount()))
                .limit(5)
                .toList();
        vo.setTopProjects(top.stream().map(p -> {
            DashboardVO.ProjectRow row = new DashboardVO.ProjectRow();
            row.setProjectNo(p.getProjectNo());
            row.setProjectName(p.getProjectName());
            row.setContractAmount(p.getContractAmount());
            row.setTotalCollected(p.getTotalCollected());
            row.setProgressPercent(CollectionSummaryProgress.percent(p.getTotalCollected(), p.getContractAmount()));
            return row;
        }).toList());

        return ApiResult.success(vo);
    }

    /** 数据体检：待补全/待处理的数据项(管理员与合伙人体视角含全局项,员工仅本人草稿) */
    @GetMapping("/health-check")
    public ApiResult<List<java.util.Map<String, Object>>> healthCheck(@AuthenticationPrincipal SecurityUser user) {
        List<java.util.Map<String, Object>> items = new java.util.ArrayList<>();
        boolean globalViewer = user.hasRole("admin") || user.hasRole("manager") || user.hasRole("partner");
        if (globalViewer) {
            Long invoicesNoNumber = invoiceMapper.selectCount(new LambdaQueryWrapper<Invoice>()
                    .eq(Invoice::getStatus, InvoiceStatus.ISSUED.getCode())
                    .and(w -> w.isNull(Invoice::getInvoiceNo).or().eq(Invoice::getInvoiceNo, "")));
            addHealthItem(items, "invoice-no", "已开票待补发票号", invoicesNoNumber, "/business/invoice");

            Long contractNoTax = contractMapper.selectCount(new LambdaQueryWrapper<Contract>()
                    .eq(Contract::getStatus, ContractStatus.RUNNING.getCode())
                    .isNull(Contract::getTaxRate));
            addHealthItem(items, "contract-tax", "执行中合同未填税率", contractNoTax, "/business/contract");

            Long stagnant = reimbursementMapper.selectCount(new LambdaQueryWrapper<Reimbursement>()
                    .eq(Reimbursement::getStatus, 1)
                    .le(Reimbursement::getCreateTime, LocalDateTime.now().minusDays(3)));
            addHealthItem(items, "stagnant-bill", "滞留超3天的待审批报销", stagnant, "/business/reimbursement");
        }
        Long oldDrafts = reimbursementMapper.selectCount(new LambdaQueryWrapper<Reimbursement>()
                .eq(Reimbursement::getStatus, 0)
                .eq(Reimbursement::getApplicantId, user.getUserId())
                .le(Reimbursement::getCreateTime, LocalDateTime.now().minusDays(7)));
        addHealthItem(items, "old-draft", "超过7天未提交的报销草稿", oldDrafts, "/business/reimbursement");

        // 项目工时超预算：当年推算工时 > 预算工时
        try {
            int year = java.time.LocalDate.now().getYear();
            var budgeted = projectMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.accounting.firm.project.entity.Project>()
                    .isNotNull(com.accounting.firm.project.entity.Project::getBudgetHours));
            var schedules = scheduleMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.accounting.firm.schedule.entity.Schedule>()
                    .ge(com.accounting.firm.schedule.entity.Schedule::getScheduleDate, java.time.LocalDate.of(year, 1, 1))
                    .le(com.accounting.firm.schedule.entity.Schedule::getScheduleDate, java.time.LocalDate.of(year, 12, 31))
                    .isNotNull(com.accounting.firm.schedule.entity.Schedule::getProjectId));
            java.util.Map<Long, java.math.BigDecimal> actual = new java.util.LinkedHashMap<>();
            for (var s : schedules) {
                actual.merge(s.getProjectId(), com.accounting.firm.schedule.service.ScheduleHoursCalculator.effectiveHours(s),
                        java.math.BigDecimal::add);
            }
            int over = 0;
            for (var p : budgeted) {
                if (p.getBudgetHours() != null && p.getBudgetHours().signum() > 0
                        && actual.getOrDefault(p.getId(), java.math.BigDecimal.ZERO).compareTo(p.getBudgetHours()) > 0) {
                    over++;
                }
            }
            addHealthItem(items, "budget-hours", "项目工时超预算", (long) over, "/business/cost");
        } catch (Exception e) {
            addHealthItem(items, "budget-hours", "项目工时超预算", 0L, "/business/cost");
        }
        return ApiResult.success(items);
    }

    private void addHealthItem(List<java.util.Map<String, Object>> items,
                               String key, String title, Long count, String path) {
        if (count != null && count > 0) {
            items.add(Map.of("key", key, "title", title, "count", count, "path", path));
        }
    }

    private long countReimbursementPending(DataScopeService.Scope scope) {
        LambdaQueryWrapper<Reimbursement> wrapper = new LambdaQueryWrapper<Reimbursement>()
                .in(Reimbursement::getStatus, 1, 4);
        applyProjectDeptScope(wrapper, scope, Reimbursement::getProjectId, Reimbursement::getCreateBy);
        return reimbursementMapper.selectCount(wrapper);
    }

    private long countPendingInvoices(DataScopeService.Scope scope) {
        LambdaQueryWrapper<Invoice> wrapper = new LambdaQueryWrapper<Invoice>()
                .eq(Invoice::getStatus, InvoiceStatus.PENDING.getCode());
        // 发票经合同挂项目：按合同的 project_id 关联项目部门
        switch (scope.type()) {
            case DEPT -> wrapper.inSql(Invoice::getContractId,
                    "SELECT id FROM contract WHERE project_id IN (" + scope.projectDeptInSql() + ")");
            case SELF -> wrapper.eq(Invoice::getCreateBy, scope.username());
            default -> { }
        }
        return invoiceMapper.selectCount(wrapper);
    }

    private long countOverdueReceivables(LocalDate today) {
        LocalDate deadline = today.minusDays(30);
        List<Invoice> overdue = invoiceMapper.selectList(new LambdaQueryWrapper<Invoice>()
                .eq(Invoice::getStatus, InvoiceStatus.ISSUED.getCode())
                .le(Invoice::getInvoiceDate, deadline));
        return overdue.stream().filter(inv -> {
            BigDecimal collected = paymentMapper.selectList(new LambdaQueryWrapper<ContractPayment>()
                            .eq(ContractPayment::getInvoiceId, inv.getId()))
                    .stream().map(p -> p.getAmount() == null ? BigDecimal.ZERO : p.getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return inv.getAmount() == null || inv.getAmount().subtract(collected).signum() > 0;
        }).count();
    }

    private long countOverdueConfirmations(LocalDate today, DataScopeService.Scope scope) {
        LambdaQueryWrapper<Confirmation> wrapper = new LambdaQueryWrapper<Confirmation>()
                .eq(Confirmation::getStatus, 1)
                .le(Confirmation::getSentDate, today.minusDays(30));
        applyProjectDeptScope(wrapper, scope, Confirmation::getProjectId, Confirmation::getCreateBy);
        return confirmationMapper.selectCount(wrapper);
    }

    private long countExpiringContracts(LocalDate today, DataScopeService.Scope scope) {
        LambdaQueryWrapper<Contract> wrapper = new LambdaQueryWrapper<Contract>()
                .eq(Contract::getStatus, ContractStatus.RUNNING.getCode())
                .ge(Contract::getServiceEnd, today)
                .le(Contract::getServiceEnd, today.plusDays(30));
        applyProjectDeptScope(wrapper, scope, Contract::getProjectId, Contract::getCreateBy);
        return contractMapper.selectCount(wrapper);
    }

    /**
     * 按项目部门套用数据范围：DEPT → project_id 命中本部门项目（无项目的数据视为公共，一并计入）；
     * SELF → 仅本人创建；ALL → 不加条件
     */
    private <T> void applyProjectDeptScope(LambdaQueryWrapper<T> wrapper, DataScopeService.Scope scope,
                                           SFunction<T, Long> projectIdGetter, SFunction<T, String> createByGetter) {
        switch (scope.type()) {
            case DEPT -> wrapper.and(w -> w.inSql(projectIdGetter, scope.projectDeptInSql())
                    .or().isNull(projectIdGetter));
            case SELF -> wrapper.eq(createByGetter, scope.username());
            default -> { }
        }
    }

    /** 回款进度百分比纯计算（聚合复用） */
    static final class CollectionSummaryProgress {
        private CollectionSummaryProgress() {
        }

        static Integer percent(BigDecimal collected, BigDecimal contractAmount) {
            if (contractAmount == null || contractAmount.signum() == 0) {
                return 0;
            }
            return collected.multiply(BigDecimal.valueOf(100))
                    .divide(contractAmount, 0, java.math.RoundingMode.HALF_UP)
                    .intValue();
        }
    }
}
