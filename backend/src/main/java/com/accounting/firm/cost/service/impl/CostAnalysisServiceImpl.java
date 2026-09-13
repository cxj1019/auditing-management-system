package com.accounting.firm.cost.service.impl;

import com.accounting.firm.common.api.PageResult;
import com.accounting.firm.common.exception.BusinessException;
import com.accounting.firm.cost.dto.LaborCostRequest;
import com.accounting.firm.cost.dto.OverviewVO;
import com.accounting.firm.cost.dto.ProjectProfitVO;
import com.accounting.firm.cost.entity.LaborCost;
import com.accounting.firm.cost.mapper.CostAnalysisMapper;
import com.accounting.firm.cost.mapper.LaborCostMapper;
import com.accounting.firm.cost.service.CostAnalysisService;
import com.accounting.firm.common.security.DataScopeService;
import com.accounting.firm.common.security.SecurityUser;
import com.accounting.firm.project.entity.Project;
import com.accounting.firm.project.entity.ProjectStatus;
import com.accounting.firm.project.mapper.ProjectMapper;
import com.accounting.firm.schedule.entity.Schedule;
import com.accounting.firm.schedule.mapper.ScheduleMapper;
import com.accounting.firm.schedule.service.ScheduleHoursCalculator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 成本分析服务实现
 */
@Service
@RequiredArgsConstructor
public class CostAnalysisServiceImpl extends ServiceImpl<LaborCostMapper, LaborCost>
        implements CostAnalysisService {

    private final CostAnalysisMapper costAnalysisMapper;
    private final DataScopeService dataScopeService;
    private final ProjectMapper projectMapper;
    private final ScheduleMapper scheduleMapper;
    private final com.accounting.firm.cost.mapper.LaborRateMapper laborRateMapper;
    private final com.accounting.firm.system.mapper.StaffLevelMapper staffLevelMapper;
    private final com.accounting.firm.system.mapper.SysUserMapper sysUserMapper;

    @Override
    public List<ProjectProfitVO> projectProfit(String keyword, Integer year) {
        ProjectScope scope = projectScope();
        List<ProjectProfitVO> rows = costAnalysisMapper.selectProjectProfit(keyword, scope.deptId(), scope.ownUsername(), year);
        // 工时自动人工成本 = Σ(推算工时 × 人员单价)；手工登记额作为"人工成本调整"叠加
        Map<Long, BigDecimal> autoLabor = autoLaborByProject(year);
        Map<Long, BigDecimal> actualHours = actualHoursByProject(year);
        for (ProjectProfitVO row : rows) {
            BigDecimal auto = autoLabor.getOrDefault(row.getProjectId(), BigDecimal.ZERO);
            row.setAutoLaborCost(auto);
            row.setLaborCost(nvl(row.getLaborCost()).add(auto));
            row.setActualHours(actualHours.getOrDefault(row.getProjectId(), BigDecimal.ZERO));
        }
        rows.forEach(ProjectProfitVO::fillDerived);
        return rows;
    }

    /** 当年各项目实际投入工时（规则推算） */
    private Map<Long, BigDecimal> actualHoursByProject(Integer year) {
        LambdaQueryWrapper<Schedule> wrapper = new LambdaQueryWrapper<Schedule>()
                .isNotNull(Schedule::getProjectId);
        if (year != null) {
            wrapper.ge(Schedule::getScheduleDate, java.time.LocalDate.of(year, 1, 1))
                   .le(Schedule::getScheduleDate, java.time.LocalDate.of(year, 12, 31));
        }
        Map<Long, BigDecimal> result = new java.util.LinkedHashMap<>();
        for (Schedule s : scheduleMapper.selectList(wrapper)) {
            result.merge(s.getProjectId(), ScheduleHoursCalculator.effectiveHours(s), BigDecimal::add);
        }
        return result;
    }

    /** 按项目汇总工时自动人工成本（推算工时 × 单价）：优先个人单价，否则按员工级别标准单价 */
    private Map<Long, BigDecimal> autoLaborByProject(Integer year) {
        // 级别标准单价
        Map<Long, BigDecimal> levelRate = staffLevelMapper.selectList(null).stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.accounting.firm.system.entity.StaffLevel::getId,
                        l -> l.getHourlyRate() == null ? BigDecimal.ZERO : l.getHourlyRate(),
                        (a, b) -> a));
        Map<Long, BigDecimal> userRate = new java.util.LinkedHashMap<>();
        for (com.accounting.firm.system.entity.SysUser u : sysUserMapper.selectList(null)) {
            BigDecimal r = levelRate.getOrDefault(u.getStaffLevelId(), BigDecimal.ZERO);
            userRate.put(u.getId(), r == null ? BigDecimal.ZERO : r);
        }
        if (userRate.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<Schedule> wrapper = new LambdaQueryWrapper<Schedule>()
                .isNotNull(Schedule::getProjectId);
        if (year != null) {
            wrapper.ge(Schedule::getScheduleDate, java.time.LocalDate.of(year, 1, 1))
                   .le(Schedule::getScheduleDate, java.time.LocalDate.of(year, 12, 31));
        }
        List<Schedule> schedules = scheduleMapper.selectList(wrapper);
        Map<Long, BigDecimal> result = new java.util.LinkedHashMap<>();
        for (Schedule s : schedules) {
            BigDecimal rate = userRate.get(s.getUserId());
            if (rate == null || rate.signum() <= 0) continue;
            result.merge(s.getProjectId(), ScheduleHoursCalculator.effectiveHours(s).multiply(rate), BigDecimal::add);
        }
        return result;
    }

    /** 项目工时汇总（按规则推算的各项目日程工时，含部门隔离），供导出 */
    @Override
    public List<java.util.Map<String, Object>> projectHours(String keyword, Integer year) {
        List<java.util.Map<String, Object>> details = projectHourDetails(keyword, year);
        Map<Long, BigDecimal> hoursByProject = new java.util.LinkedHashMap<>();
        List<java.util.Map<String, Object>> summary = new java.util.ArrayList<>();
        for (java.util.Map<String, Object> d : details) {
            Long pid = (Long) d.get("projectId");
            hoursByProject.merge(pid, (BigDecimal) d.get("totalHours"), BigDecimal::add);
            if (!hoursByProject.containsKey(pid) || hoursByProject.get(pid).compareTo(BigDecimal.ZERO) >= 0) {
                boolean seen = summary.stream().anyMatch(r -> r.get("projectId").equals(pid));
                if (!seen) {
                    java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("projectId", pid);
                    row.put("projectNo", d.get("projectNo"));
                    row.put("projectName", d.get("projectName"));
                    row.put("clientName", d.get("clientName"));
                    summary.add(row);
                }
            }
        }
        for (java.util.Map<String, Object> row : summary) {
            row.put("totalHours", hoursByProject.getOrDefault((Long) row.get("projectId"), BigDecimal.ZERO));
        }
        return summary;
    }

    @Override
    public List<java.util.Map<String, Object>> laborRates() {
        List<com.accounting.firm.system.entity.StaffLevel> levels = staffLevelMapper.selectList(
                new LambdaQueryWrapper<com.accounting.firm.system.entity.StaffLevel>()
                        .orderByAsc(com.accounting.firm.system.entity.StaffLevel::getSort));
        Map<Long, com.accounting.firm.system.entity.StaffLevel> levelById = levels.stream()
                .collect(java.util.stream.Collectors.toMap(com.accounting.firm.system.entity.StaffLevel::getId, l -> l));
        List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (com.accounting.firm.system.entity.SysUser u : sysUserMapper.selectList(null)) {
            String name = u.getNickname() != null && !u.getNickname().isEmpty() ? u.getNickname() : u.getUsername();
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("userId", u.getId());
            row.put("userName", name);
            row.put("staffLevelId", u.getStaffLevelId());
            row.put("levelName", u.getStaffLevelId() != null && levelById.containsKey(u.getStaffLevelId())
                    ? levelById.get(u.getStaffLevelId()).getName() : null);
            row.put("effectiveRate", effectiveRateOf(u, levelById));
            result.add(row);
        }
        result.sort((a, b) -> String.valueOf(a.get("userName")).compareTo(String.valueOf(b.get("userName"))));
        return result;
    }

    /** 生效单价：成员级别标准单价（未定级为 0，不计人工成本） */
    private BigDecimal effectiveRateOf(com.accounting.firm.system.entity.SysUser u,
                                       Map<Long, com.accounting.firm.system.entity.StaffLevel> levelById) {
        if (u.getStaffLevelId() != null && levelById.containsKey(u.getStaffLevelId())) {
            BigDecimal r = levelById.get(u.getStaffLevelId()).getHourlyRate();
            return r == null ? BigDecimal.ZERO : r;
        }
        return BigDecimal.ZERO;
    }

    @Override
    public void saveLaborRates(List<com.accounting.firm.cost.dto.LaborRateItem> rates, String operator) {
        // 已改为级别制：个人单价不再参与计算；该方法保留兼容旧调用，仅清空传入即可
        for (var item : rates) {
            if (item.getUserId() == null || item.getHourlyRate() == null) continue;
            com.accounting.firm.cost.entity.LaborRate existing = laborRateMapper.selectOne(
                    new LambdaQueryWrapper<com.accounting.firm.cost.entity.LaborRate>()
                            .eq(com.accounting.firm.cost.entity.LaborRate::getUserId, item.getUserId()));
            if (existing == null) {
                com.accounting.firm.cost.entity.LaborRate rate = new com.accounting.firm.cost.entity.LaborRate();
                rate.setUserId(item.getUserId());
                rate.setHourlyRate(item.getHourlyRate());
                rate.setUpdateBy(operator);
                rate.setUpdateTime(java.time.LocalDateTime.now());
                laborRateMapper.insert(rate);
            } else {
                existing.setHourlyRate(item.getHourlyRate());
                existing.setUpdateBy(operator);
                existing.setUpdateTime(java.time.LocalDateTime.now());
                laborRateMapper.updateById(existing);
            }
        }
    }

    @Override
    public List<com.accounting.firm.system.entity.StaffLevel> staffLevels() {
        return staffLevelMapper.selectList(new LambdaQueryWrapper<com.accounting.firm.system.entity.StaffLevel>()
                .orderByAsc(com.accounting.firm.system.entity.StaffLevel::getSort));
    }

    @Override
    public void saveStaffLevels(List<com.accounting.firm.system.entity.StaffLevel> levels) {
        for (com.accounting.firm.system.entity.StaffLevel level : levels) {
            if (level.getName() == null || level.getName().isBlank()) continue;
            if (level.getId() == null) {
                com.accounting.firm.system.entity.StaffLevel fresh = new com.accounting.firm.system.entity.StaffLevel();
                fresh.setName(level.getName().trim());
                fresh.setHourlyRate(level.getHourlyRate() == null ? BigDecimal.ZERO : level.getHourlyRate());
                fresh.setSort(level.getSort() == null ? 999 : level.getSort());
                staffLevelMapper.insert(fresh);
            } else {
                com.accounting.firm.system.entity.StaffLevel existing = staffLevelMapper.selectById(level.getId());
                if (existing == null) continue;
                existing.setName(level.getName().trim());
                existing.setHourlyRate(level.getHourlyRate() == null ? BigDecimal.ZERO : level.getHourlyRate());
                if (level.getSort() != null) existing.setSort(level.getSort());
                staffLevelMapper.updateById(existing);
            }
        }
    }

    @Override
    public void saveUserLevels(List<com.accounting.firm.cost.dto.LaborRateItem> assignments) {
        for (var item : assignments) {
            if (item.getUserId() == null) continue;
            com.accounting.firm.system.entity.SysUser user = sysUserMapper.selectById(item.getUserId());
            if (user == null) continue;
            user.setStaffLevelId(item.getStaffLevelId());
            sysUserMapper.updateById(user);
        }
    }

    /** 人员工时明细：项目 × 人员 的规则推算工时（含部门隔离），供导出 */
    @Override
    public List<java.util.Map<String, Object>> projectHourDetails(String keyword, Integer year) {
        ProjectScope scope = projectScope();
        List<ProjectProfitVO> rows = costAnalysisMapper.selectProjectProfit(keyword, scope.deptId(), scope.ownUsername(), year);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> projectIds = rows.stream().map(ProjectProfitVO::getProjectId).toList();
        Map<Long, ProjectProfitVO> rowByProject = rows.stream()
                .collect(java.util.stream.Collectors.toMap(ProjectProfitVO::getProjectId, java.util.function.Function.identity()));
        List<Schedule> schedules = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .in(Schedule::getProjectId, projectIds));
        // (projectId|userId) → 分钟数
        Map<String, long[]> agg = new java.util.LinkedHashMap<>();
        for (Schedule s : schedules) {
            if (s.getProjectId() == null || s.getUserId() == null) {
                continue;
            }
            long minutes = ScheduleHoursCalculator.effectiveHours(s).multiply(BigDecimal.valueOf(60)).longValue();
            agg.computeIfAbsent(s.getProjectId() + "|" + s.getUserId(), k -> new long[1])[0] += minutes;
        }
        // 人员姓名
        List<Long> userIds = schedules.stream().map(Schedule::getUserId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> nicknameById = userIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(userIds).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                com.accounting.firm.system.entity.SysUser::getId,
                                u -> u.getNickname() != null ? u.getNickname() : u.getUsername()));
        List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (var entry : agg.entrySet()) {
            String[] parts = entry.getKey().split("\\|");
            Long projectId = Long.parseLong(parts[0]);
            Long userId = Long.parseLong(parts[1]);
            ProjectProfitVO row = rowByProject.get(projectId);
            if (row == null) {
                continue;
            }
            java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("projectId", projectId);
            item.put("projectNo", row.getProjectNo());
            item.put("projectName", row.getProjectName());
            item.put("clientName", row.getClientName());
            item.put("memberName", nicknameById.getOrDefault(userId, "用户" + userId));
            item.put("totalHours", BigDecimal.valueOf(entry.getValue()[0])
                    .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP));
            result.add(item);
        }
        result.sort((a, b) -> String.valueOf(a.get("projectNo")).compareTo(String.valueOf(b.get("projectNo"))));
        return result;
    }

    /** 项目数据范围：管理员看全部；有部门的经理/合伙人看本部门项目的数据；无部门的非管理员只看自己创建的项目 */
    private ProjectScope projectScope() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof SecurityUser user && !user.hasRole("admin")) {
            if (user.getDeptId() != null) {
                return new ProjectScope(user.getDeptId(), null);
            }
            return new ProjectScope(null, user.getUsername());
        }
        return new ProjectScope(null, null);
    }

    private record ProjectScope(Long deptId, String ownUsername) {
    }

    @Override
    public List<java.util.Map<String, Object>> monthlyTrend() {
        return costAnalysisMapper.selectMonthlyTrend();
    }

    @Override
    public List<com.accounting.firm.cost.dto.ExpenseStatVO> expenseStats(Integer year) {
        // 与项目数据范围同规则：按申请人归属过滤（admin 全部；本部门；无部门仅本人）
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        List<Long> userIds = null;
        Long selfUserId = null;
        if (auth != null && auth.getPrincipal() instanceof SecurityUser user && !user.hasRole("admin")) {
            var scope = dataScopeService.currentScope();
            switch (scope.type()) {
                case DEPT -> userIds = dataScopeService.getDeptScopedUserIds();
                case SELF -> selfUserId = user.getUserId();
                default -> { }
            }
        }
        return costAnalysisMapper.selectExpenseStats(year, userIds, selfUserId);
    }

    @Override
    public OverviewVO overview() {
        List<ProjectProfitVO> all = projectProfit(null, null);
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalContractAmount = BigDecimal.ZERO;
        for (ProjectProfitVO row : all) {
            totalIncome = totalIncome.add(nvl(row.getTotalCollected()));
            totalCost = totalCost.add(nvl(row.getDirectCost()));
            totalContractAmount = totalContractAmount.add(nvl(row.getContractAmount()));
        }
        OverviewVO vo = new OverviewVO();
        vo.setTotalIncome(totalIncome);
        vo.setTotalCost(totalCost);
        vo.setGrossProfit(totalIncome.subtract(totalCost));
        // 回款率：总收款 / 全部合同金额合计；无合同时为 null
        if (totalContractAmount.signum() == 0) {
            vo.setCollectionRate(null);
        } else {
            vo.setCollectionRate(totalIncome.multiply(BigDecimal.valueOf(100))
                    .divide(totalContractAmount, 2, RoundingMode.HALF_UP));
        }
        return vo;
    }

    @Override
    public PageResult<LaborCost> pageLaborCosts(long current, long size, Long projectId) {
        LambdaQueryWrapper<LaborCost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, LaborCost::getProjectId, projectId)
                .orderByDesc(LaborCost::getCostMonth)
                .orderByAsc(LaborCost::getPersonName);
        Page<LaborCost> page = page(new Page<>(current, size), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public void addLaborCost(LaborCostRequest request) {
        requireValidProject(request.getProjectId());
        requireNotDuplicate(request, null);
        LaborCost laborCost = new LaborCost();
        copyFields(request, laborCost);
        try {
            save(laborCost);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("该人员在此月份的人工成本已存在");
        }
    }

    @Override
    public void updateLaborCost(Long id, LaborCostRequest request) {
        LaborCost laborCost = getById(id);
        if (laborCost == null) {
            throw new BusinessException("人工成本记录不存在");
        }
        requireValidProject(request.getProjectId());
        requireNotDuplicate(request, id);
        copyFields(request, laborCost);
        try {
            updateById(laborCost);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("该人员在此月份的人工成本已存在");
        }
    }

    @Override
    public void deleteLaborCost(Long id) {
        if (getById(id) == null) {
            throw new BusinessException("人工成本记录不存在");
        }
        removeById(id);
    }

    /** 校验项目存在且未归档 */
    private void requireValidProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("所属项目不存在");
        }
        if (project.getStatus() == ProjectStatus.ARCHIVED.getCode()) {
            throw new BusinessException("项目已归档，不可登记人工成本");
        }
    }

    /** 校验同项目同人同月不重复（排除自身） */
    private void requireNotDuplicate(LaborCostRequest request, Long excludeId) {
        Long count = lambdaQuery()
                .eq(LaborCost::getProjectId, request.getProjectId())
                .eq(LaborCost::getPersonName, request.getPersonName())
                .eq(LaborCost::getCostMonth, request.getCostMonth())
                .ne(excludeId != null, LaborCost::getId, excludeId)
                .count();
        if (count > 0) {
            throw new BusinessException("该人员在此月份的人工成本已存在");
        }
    }

    /** 复制可编辑字段 */
    private void copyFields(LaborCostRequest request, LaborCost laborCost) {
        laborCost.setProjectId(request.getProjectId());
        laborCost.setPersonName(request.getPersonName());
        laborCost.setCostMonth(request.getCostMonth());
        laborCost.setAmount(request.getAmount());
        laborCost.setRemark(request.getRemark());
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
