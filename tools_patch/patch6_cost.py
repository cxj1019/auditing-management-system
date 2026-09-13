import io
p = r'backend/src/main/java/com/accounting/firm/cost/service/impl/CostAnalysisServiceImpl.java'
s = io.open(p, encoding='utf-8').read()

# ===== 1) projectProfit：并入工时自动人工成本 =====
old = """    @Override
    public List<ProjectProfitVO> projectProfit(String keyword, Integer year) {
        ProjectScope scope = projectScope();
        List<ProjectProfitVO> rows = costAnalysisMapper.selectProjectProfit(keyword, scope.deptId(), scope.ownUsername(), year);
        rows.forEach(ProjectProfitVO::fillDerived);
        return rows;
    }"""
new = """    @Override
    public List<ProjectProfitVO> projectProfit(String keyword, Integer year) {
        ProjectScope scope = projectScope();
        List<ProjectProfitVO> rows = costAnalysisMapper.selectProjectProfit(keyword, scope.deptId(), scope.ownUsername(), year);
        // 工时自动人工成本 = Σ(推算工时 × 人员单价)；手工登记额作为"人工成本调整"叠加
        Map<Long, BigDecimal> autoLabor = autoLaborByProject(year);
        for (ProjectProfitVO row : rows) {
            BigDecimal auto = autoLabor.getOrDefault(row.getProjectId(), BigDecimal.ZERO);
            row.setAutoLaborCost(auto);
            row.setLaborCost(nvl(row.getLaborCost()).add(auto));
        }
        rows.forEach(ProjectProfitVO::fillDerived);
        return rows;
    }

    /** 按项目汇总工时自动人工成本（推算工时 × 人员单价） */
    private Map<Long, BigDecimal> autoLaborByProject(Integer year) {
        Map<Long, com.accounting.firm.cost.entity.LaborRate> rateByUser = laborRateMapper.selectList(null)
                .stream().collect(java.util.stream.Collectors.toMap(
                        com.accounting.firm.cost.entity.LaborRate::getUserId,
                        r -> r,
                        (a, b) -> a));
        if (rateByUser.isEmpty()) {
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
            com.accounting.firm.cost.entity.LaborRate rate = rateByUser.get(s.getUserId());
            if (rate == null || rate.getHourlyRate() == null) continue;
            BigDecimal cost = ScheduleHoursCalculator.effectiveHours(s).multiply(rate.getHourlyRate());
            result.merge(s.getProjectId(), cost, BigDecimal::add);
        }
        return result;
    }"""
assert old in s
s = s.replace(old, new)

# 依赖：laborRateMapper + ScheduleHoursCalculator
if 'laborRateMapper' not in s.split('projectProfit')[0]:
    s = s.replace(
"""    private final ScheduleMapper scheduleMapper;""",
"""    private final ScheduleMapper scheduleMapper;
    private final com.accounting.firm.cost.mapper.LaborRateMapper laborRateMapper;""")
if 'ScheduleHoursCalculator' not in s.split('public List<ProjectProfitVO>')[0]:
    s = s.replace(
"""import com.accounting.firm.project.mapper.ProjectMapper;""",
"""import com.accounting.firm.project.mapper.ProjectMapper;
import com.accounting.firm.schedule.service.ScheduleHoursCalculator;""")

# ===== 2) 单价清单/保存 =====
old2 = """    /** 人员工时明细：项目 × 人员 的规则推算工时（含部门隔离），供导出 */"""
new2 = """    @Override
    public List<java.util.Map<String, Object>> laborRates() {
        Map<Long, String> names = sysUserMapper.selectList(null).stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.accounting.firm.system.entity.SysUser::getId,
                        u -> u.getNickname() != null && !u.getNickname().isEmpty() ? u.getNickname() : u.getUsername(),
                        (a, b) -> a));
        Map<Long, BigDecimal> rateById = laborRateMapper.selectList(null).stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.accounting.firm.cost.entity.LaborRate::getUserId,
                        com.accounting.firm.cost.entity.LaborRate::getHourlyRate,
                        (a, b) -> a));
        List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (var entry : names.entrySet()) {
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("userId", entry.getKey());
            row.put("userName", entry.getValue());
            row.put("hourlyRate", rateById.getOrDefault(entry.getKey(), BigDecimal.ZERO));
            result.add(row);
        }
        result.sort((a, b) -> String.valueOf(a.get("userName")).compareTo(String.valueOf(b.get("userName"))));
        return result;
    }

    @Override
    public void saveLaborRates(List<com.accounting.firm.cost.dto.LaborRateItem> rates, String operator) {
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

    /** 人员工时明细：项目 × 人员 的规则推算工时（含部门隔离），供导出 */"""
assert old2 in s
s = s.replace(old2, new2)

# LambdaQueryWrapper import present?
if 'import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;' not in s:
    s = s.replace(
"""import com.accounting.firm.common.api.PageResult;""",
"""import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.accounting.firm.common.api.PageResult;""", 1)

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('cost service ok')
