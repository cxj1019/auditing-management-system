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
