import io
p = r'backend/src/main/java/com/accounting/firm/schedule/service/impl/ScheduleServiceImpl.java'
s = io.open(p, encoding='utf-8').read()

old = """        for (var entry : hoursByUser.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", entry.getKey());
            row.put("memberName", nameByUser.getOrDefault(entry.getKey(), ""));
            row.put("totalHours", entry.getValue());
            result.add(row);
        }
        return result;
    }"""
new = """        for (var entry : hoursByUser.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", entry.getKey());
            row.put("memberName", nameByUser.getOrDefault(entry.getKey(), ""));
            row.put("totalHours", entry.getValue());
            row.put("overtimeHours", overtimeByUser.getOrDefault(entry.getKey(), BigDecimal.ZERO));
            row.put("stdHours", stdHours);
            row.put("utilization", stdHours.signum() > 0
                    ? entry.getValue().multiply(BigDecimal.valueOf(100)).divide(stdHours, 1, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
            row.put("confirmedCount", list(new LambdaQueryWrapper<Schedule>()
                    .eq(Schedule::getUserId, entry.getKey())
                    .eq(Schedule::getConfirmed, 1)
                    .ge(startDate != null, Schedule::getScheduleDate, startDate)
                    .le(endDate != null, Schedule::getScheduleDate, endDate)).size());
            result.add(row);
        }
        return result;
    }"""
assert old in s
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('summary fields ok')
