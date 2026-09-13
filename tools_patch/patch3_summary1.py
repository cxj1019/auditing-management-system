import io
p = r'backend/src/main/java/com/accounting/firm/schedule/service/impl/ScheduleServiceImpl.java'
s = io.open(p, encoding='utf-8').read()

old = """        List<Schedule> schedules = list(wrapper);

        // 按成员聚合，工时按规则推算（全天 7 小时/有时间按净工时/加班 4 小时强制休息 1 小时）
        Map<Long, BigDecimal> hoursByUser = new LinkedHashMap<>();
        for (Schedule s : schedules) {
            hoursByUser.merge(s.getUserId(), ScheduleHoursCalculator.effectiveHours(s), BigDecimal::add);
        }"""

new = """        List<Schedule> schedules = list(wrapper);

        // 按成员聚合，工时按规则推算（全天 7 小时/有时间按净工时/加班 4 小时强制休息 1 小时）
        Map<Long, BigDecimal> hoursByUser = new LinkedHashMap<>();
        Map<Long, BigDecimal> overtimeByUser = new LinkedHashMap<>();
        for (Schedule s : schedules) {
            BigDecimal h = ScheduleHoursCalculator.effectiveHours(s);
            hoursByUser.merge(s.getUserId(), h, BigDecimal::add);
            if ("加班".equals(s.getType())) {
                overtimeByUser.merge(s.getUserId(), h, BigDecimal::add);
            }
        }
        // 标准工时 = 月数 × 174h（21.75 天 × 8h），利用率 = 实际 / 标准
        long months = 1;
        if (startDate != null && endDate != null) {
            months = java.time.temporal.ChronoUnit.MONTHS.between(
                    startDate.withDayOfMonth(1), endDate.withDayOfMonth(1)) + 1;
        }
        BigDecimal stdHours = BigDecimal.valueOf(174).multiply(BigDecimal.valueOf(months));"""

assert old in s
s = s.replace(old, new)

# 结果行补充字段（找到组装 result 的循环）
old2 = """        List<Map<String, Object>> result = new ArrayList<>();
        for (var entry : hoursByUser.entrySet()) {"""
new2 = """        List<Map<String, Object>> result = new ArrayList<>();
        for (var entry : hoursByUser.entrySet()) {
            BigDecimal total = entry.getValue();
            BigDecimal overtime = overtimeByUser.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            BigDecimal utilization = stdHours.signum() > 0
                    ? total.multiply(BigDecimal.valueOf(100)).divide(stdHours, 1, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;"""
assert old2 in s
s = s.replace(old2, new2)

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('hoursSummary patch part1 ok')
