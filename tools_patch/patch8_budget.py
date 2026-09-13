import io

# ===== selectProjectProfit SQL 带出 budget_hours =====
p = r'backend/src/main/java/com/accounting/firm/cost/mapper/CostAnalysisMapper.java'
s = io.open(p, encoding='utf-8').read()
old = """            SELECT p.id AS project_id, p.project_no, p.name AS project_name, cl.client_name,
                   COALESCE(amt.contract_amount, 0) AS contract_amount,"""
new = """            SELECT p.id AS project_id, p.project_no, p.name AS project_name, cl.client_name,
                   p.budget_hours AS budget_hours,
                   COALESCE(amt.contract_amount, 0) AS contract_amount,"""
assert old in s
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('mapper ok')

# ===== projectProfit 填 actualHours（当年推算工时，全部成员） =====
p = r'backend/src/main/java/com/accounting/firm/cost/service/impl/CostAnalysisServiceImpl.java'
s = io.open(p, encoding='utf-8').read()
old = """        // 工时自动人工成本 = Σ(推算工时 × 人员单价)；手工登记额作为"人工成本调整"叠加
        Map<Long, BigDecimal> autoLabor = autoLaborByProject(year);
        for (ProjectProfitVO row : rows) {
            BigDecimal auto = autoLabor.getOrDefault(row.getProjectId(), BigDecimal.ZERO);
            row.setAutoLaborCost(auto);
            row.setLaborCost(nvl(row.getLaborCost()).add(auto));
        }
        rows.forEach(ProjectProfitVO::fillDerived);
        return rows;
    }"""
new = """        // 工时自动人工成本 = Σ(推算工时 × 人员单价)；手工登记额作为"人工成本调整"叠加
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
    }"""
assert old in s
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('cost service ok')
