import io
p = r'backend/src/main/java/com/accounting/firm/schedule/service/impl/ScheduleServiceImpl.java'
s = io.open(p, encoding='utf-8').read()

# 依赖：ScheduleLockMapper
if 'ScheduleLockMapper' not in s:
    s = s.replace(
"""import com.accounting.firm.schedule.entity.Schedule;
import com.accounting.firm.schedule.entity.ScheduleResource;
import com.accounting.firm.schedule.mapper.ScheduleMapper;
import com.accounting.firm.schedule.mapper.ScheduleResourceMapper;""",
"""import com.accounting.firm.schedule.entity.Schedule;
import com.accounting.firm.schedule.entity.ScheduleLock;
import com.accounting.firm.schedule.entity.ScheduleResource;
import com.accounting.firm.schedule.mapper.ScheduleMapper;
import com.accounting.firm.schedule.mapper.ScheduleLockMapper;
import com.accounting.firm.schedule.mapper.ScheduleResourceMapper;""")
    s = s.replace(
"""    private final ScheduleResourceMapper scheduleResourceMapper;
    private final DataScopeService dataScopeService;""",
"""    private final ScheduleResourceMapper scheduleResourceMapper;
    private final ScheduleLockMapper scheduleLockMapper;
    private final DataScopeService dataScopeService;""")

# 锁定校验方法 + createSchedule 入口
if 'requireNotLocked' not in s:
    s = s.replace(
"""    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createSchedule(ScheduleRequest request, SecurityUser currentUser) {
        requireValidProject(request.getProjectId());""",
"""    /** 日程日期所在月份被锁定时禁止增删改 */
    private void requireNotLocked(java.time.LocalDate date) {
        if (date == null) return;
        String month = date.toString().substring(0, 7);
        Long locks = scheduleLockMapper.selectCount(new LambdaQueryWrapper<ScheduleLock>()
                .eq(ScheduleLock::getLockMonth, month));
        if (locks != null && locks > 0) {
            throw new BusinessException(month + " 月工时已锁定，不能修改");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createSchedule(ScheduleRequest request, SecurityUser currentUser) {
        requireValidProject(request.getProjectId());
        requireNotLocked(request.getScheduleDate());""")

# updateSchedule 入口
s = s.replace(
"""        Schedule schedule = getById(id);
        if (schedule == null) {
            throw new BusinessException("日程不存在");
        }
        requireValidProject(request.getProjectId());
        copyFields(request, schedule);
        updateById(schedule);
    }""",
"""        Schedule schedule = getById(id);
        if (schedule == null) {
            throw new BusinessException("日程不存在");
        }
        requireNotLocked(schedule.getScheduleDate());
        requireValidProject(request.getProjectId());
        copyFields(request, schedule);
        updateById(schedule);
    }""", 1)

# deleteEvent 入口
s = s.replace(
"""        if (StringUtils.hasText(schedule.getEventId())) {
            remove(new LambdaQueryWrapper<Schedule>().eq(Schedule::getEventId, schedule.getEventId()));
        } else {
            removeById(id);
        }
    }""",
"""        requireNotLocked(schedule.getScheduleDate());
        if (StringUtils.hasText(schedule.getEventId())) {
            remove(new LambdaQueryWrapper<Schedule>().eq(Schedule::getEventId, schedule.getEventId()));
        } else {
            removeById(id);
        }
    }""", 1)

# 新能力方法 + hoursSummary 前插
if 'hoursMatrix' not in s:
    s = s.replace(
"""    public List<Map<String, Object>> hoursSummary(LocalDate startDate, LocalDate endDate) {""",
"""    @Override
    public List<java.util.Map<String, Object>> hoursMatrix(LocalDate startDate, LocalDate endDate) {
        List<Long> scopeUserIds = dataScopeService.getDeptScopedUserIds();
        LambdaQueryWrapper<Schedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(startDate != null, Schedule::getScheduleDate, startDate)
                .le(endDate != null, Schedule::getScheduleDate, endDate);
        if (scopeUserIds != null) {
            wrapper.in(Schedule::getUserId, scopeUserIds);
        }
        List<Schedule> schedules = list(wrapper);
        java.util.Map<String, long[]> agg = new java.util.LinkedHashMap<>();
        List<Long> userIds = new java.util.ArrayList<>();
        List<Long> projectIds = new java.util.ArrayList<>();
        for (Schedule s : schedules) {
            if (s.getUserId() == null) continue;
            if (!userIds.contains(s.getUserId())) userIds.add(s.getUserId());
            if (s.getProjectId() != null && !projectIds.contains(s.getProjectId())) projectIds.add(s.getProjectId());
            long minutes = ScheduleHoursCalculator.effectiveHours(s).multiply(java.math.BigDecimal.valueOf(60)).longValue();
            agg.computeIfAbsent(s.getUserId() + "|" + (s.getProjectId() == null ? "0" : s.getProjectId()),
                    k -> new long[1])[0] += minutes;
        }
        Map<Long, String> nameById = memberNames(userIds);
        Map<Long, String> projectNames = projectIds.isEmpty() ? Map.of()
                : projectMapper.selectBatchIds(projectIds).stream()
                        .collect(Collectors.toMap(Project::getId, Project::getName));
        List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (var entry : agg.entrySet()) {
            String[] parts = entry.getKey().split("\\\\|");
            Long userId = Long.parseLong(parts[0]);
            Long projectId = Long.parseLong(parts[1]);
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("userId", userId);
            row.put("userName", nameById.getOrDefault(userId, "用户" + userId));
            row.put("projectId", projectId == 0 ? null : projectId);
            row.put("projectName", projectId == 0 ? "未关联项目" : projectNames.getOrDefault(projectId, "项目" + projectId));
            row.put("hours", entry.getValue()[0] / 60.0);
            result.add(row);
        }
        result.sort((a, b) -> {
            int byUser = String.valueOf(a.get("userName")).compareTo(String.valueOf(b.get("userName")));
            return byUser != 0 ? byUser : String.valueOf(a.get("projectName")).compareTo(String.valueOf(b.get("projectName")));
        });
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int confirmHours(LocalDate startDate, LocalDate endDate, Long userId, SecurityUser currentUser) {
        LambdaQueryWrapper<Schedule> wrapper = new LambdaQueryWrapper<Schedule>()
                .ge(startDate != null, Schedule::getScheduleDate, startDate)
                .le(endDate != null, Schedule::getScheduleDate, endDate)
                .eq(userId != null, Schedule::getUserId, userId)
                .eq(Schedule::getConfirmed, 0);
        List<Schedule> targets = list(wrapper);
        for (Schedule s : targets) {
            s.setConfirmed(1);
            s.setConfirmedBy(currentUser.getNickname() != null ? currentUser.getNickname() : currentUser.getUsername());
            s.setConfirmedTime(java.time.LocalDateTime.now());
        }
        updateBatchById(targets);
        return targets.size();
    }

    @Override
    public List<String> listLocks() {
        return scheduleLockMapper.selectList(new LambdaQueryWrapper<ScheduleLock>()
                        .orderByAsc(ScheduleLock::getLockMonth))
                .stream().map(ScheduleLock::getLockMonth).toList();
    }

    @Override
    public void lockMonth(String month, SecurityUser currentUser) {
        if (month == null || !month.matches("\\\\d{4}-\\\\d{2}")) {
            throw new BusinessException("月份格式应为 YYYY-MM");
        }
        Long exists = scheduleLockMapper.selectCount(new LambdaQueryWrapper<ScheduleLock>()
                .eq(ScheduleLock::getLockMonth, month));
        if (exists != null && exists > 0) return;
        ScheduleLock lock = new ScheduleLock();
        lock.setLockMonth(month);
        lock.setLockedBy(currentUser.getUsername());
        scheduleLockMapper.insert(lock);
    }

    @Override
    public void unlockMonth(String month, SecurityUser currentUser) {
        scheduleLockMapper.delete(new LambdaQueryWrapper<ScheduleLock>()
                .eq(ScheduleLock::getLockMonth, month));
    }

    public List<Map<String, Object>> hoursSummary(LocalDate startDate, LocalDate endDate) {""")

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('schedule impl ok')
