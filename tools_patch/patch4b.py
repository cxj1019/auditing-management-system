import io
p = r'backend/src/main/java/com/accounting/firm/schedule/service/impl/ScheduleServiceImpl.java'
s = io.open(p, encoding='utf-8').read()

old = """            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", entry.getKey());
            row.put("memberName", nameByUser.getOrDefault(entry.getKey(), ""));
            row.put("totalHours", entry.getValue());
            result.add(row);"""
new = """            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", entry.getKey());
            row.put("memberName", nameByUser.getOrDefault(entry.getKey(), ""));
            row.put("totalHours", entry.getValue());
            row.put("overtimeHours", overtime);
            row.put("stdHours", stdHours);
            row.put("utilization", utilization);
            row.put("confirmedCount", list(new LambdaQueryWrapper<Schedule>()
                    .eq(Schedule::getUserId, entry.getKey())
                    .eq(Schedule::getConfirmed, 1)
                    .ge(startDate != null, Schedule::getScheduleDate, startDate)
                    .le(endDate != null, Schedule::getScheduleDate, endDate)).size());
            result.add(row);"""
assert old in s
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('summary fields ok')
