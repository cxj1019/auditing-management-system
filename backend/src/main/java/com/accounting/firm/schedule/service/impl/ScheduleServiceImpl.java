package com.accounting.firm.schedule.service.impl;

import com.accounting.firm.common.api.PageResult;
import com.accounting.firm.common.exception.BusinessException;
import com.accounting.firm.common.security.DataScopeService;
import com.accounting.firm.common.security.SecurityUser;
import com.accounting.firm.system.entity.SysUser;
import com.accounting.firm.system.mapper.SysUserMapper;
import com.accounting.firm.project.entity.Project;
import com.accounting.firm.project.mapper.ProjectMapper;
import com.accounting.firm.schedule.dto.ScheduleRequest;
import com.accounting.firm.schedule.entity.Schedule;
import com.accounting.firm.schedule.entity.ScheduleLock;
import com.accounting.firm.schedule.entity.ScheduleResource;
import com.accounting.firm.schedule.mapper.ScheduleMapper;
import com.accounting.firm.schedule.mapper.ScheduleLockMapper;
import com.accounting.firm.schedule.mapper.ScheduleResourceMapper;
import com.accounting.firm.schedule.service.ScheduleHoursCalculator;
import com.accounting.firm.schedule.service.ScheduleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl extends ServiceImpl<ScheduleMapper, Schedule> implements ScheduleService {

    private final ProjectMapper projectMapper;
    private final SysUserMapper sysUserMapper;
    private final ScheduleResourceMapper scheduleResourceMapper;
    private final ScheduleLockMapper scheduleLockMapper;
    private final DataScopeService dataScopeService;

    @Override
    public List<ScheduleResource> listResources() {
        return scheduleResourceMapper.selectList(new LambdaQueryWrapper<ScheduleResource>()
                .eq(ScheduleResource::getStatus, 1)
                .orderByAsc(ScheduleResource::getId));
    }

    @Override
    public List<Schedule> listByDateRange(LocalDate startDate, LocalDate endDate, Long projectId, Long userId) {
        LambdaQueryWrapper<Schedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(startDate != null, Schedule::getScheduleDate, startDate)
                .le(endDate != null, Schedule::getScheduleDate, endDate)
                .eq(projectId != null, Schedule::getProjectId, projectId)
                .eq(userId != null, Schedule::getUserId, userId)
                .orderByAsc(Schedule::getScheduleDate);
        List<Schedule> list = list(wrapper);
        fillNames(list);
        return list;
    }

    /** 日程日期所在月份被锁定时禁止增删改 */
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
        requireNotLocked(request.getScheduleDate());
        // 为每位选中成员创建日程；未选则默认为当前用户
        List<Long> targetUserIds = (request.getUserIds() != null && !request.getUserIds().isEmpty())
                ? request.getUserIds()
                : List.of(currentUser.getUserId());
        String eventId = java.util.UUID.randomUUID().toString().replace("-", "");
        for (Long uid : targetUserIds) {
            Schedule schedule = new Schedule();
            schedule.setUserId(uid);
            schedule.setEventId(eventId);
            schedule.setCreateBy(currentUser.getUsername());
            copyFields(request, schedule);
            save(schedule);
        }
    }

    @Override
    public void updateSchedule(Long id, ScheduleRequest request, SecurityUser currentUser) {
        Schedule schedule = getById(id);
        if (schedule == null) {
            throw new BusinessException("日程不存在");
        }
        requireNotLocked(schedule.getScheduleDate());
        requireValidProject(request.getProjectId());
        copyFields(request, schedule);
        updateById(schedule);
    }

    /** 删除整个日程（含全部参与人员），所有人可操作 */
    @Override
    public void deleteEvent(Long id) {
        Schedule schedule = getById(id);
        if (schedule == null) {
            throw new BusinessException("日程不存在");
        }
        requireNotLocked(schedule.getScheduleDate());
        if (StringUtils.hasText(schedule.getEventId())) {
            remove(new LambdaQueryWrapper<Schedule>().eq(Schedule::getEventId, schedule.getEventId()));
        } else {
            removeById(id);
        }
    }

    /** 退出日程：仅移除指定参与人员自己的这条 */
    @Override
    public void exitEvent(Long id) {
        Schedule schedule = getById(id);
        if (schedule == null) {
            throw new BusinessException("日程不存在");
        }
        removeById(id);
    }

    @Override
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
            String[] parts = entry.getKey().split("\\|");
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
        if (month == null || !month.matches("\\d{4}-\\d{2}")) {
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

    public List<Map<String, Object>> hoursSummary(LocalDate startDate, LocalDate endDate) {
        // 按日程归属成员（user_id）过滤与聚合；日程可能由管理员/项目经理代建，创建人 ≠ 归属人
        List<Long> scopeUserIds = dataScopeService.getDeptScopedUserIds();
        LambdaQueryWrapper<Schedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(startDate != null, Schedule::getScheduleDate, startDate)
                .le(endDate != null, Schedule::getScheduleDate, endDate);
        if (scopeUserIds != null) {
            wrapper.in(Schedule::getUserId, scopeUserIds);
        }
        List<Schedule> schedules = list(wrapper);

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
        BigDecimal stdHours = BigDecimal.valueOf(174).multiply(BigDecimal.valueOf(months));
        Map<Long, String> nameByUser = memberNames(hoursByUser.keySet().stream().toList());
        List<Map<String, Object>> result = new ArrayList<>();
        for (var entry : hoursByUser.entrySet()) {
            BigDecimal total = entry.getValue();
            BigDecimal overtime = overtimeByUser.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            BigDecimal utilization = stdHours.signum() > 0
                    ? total.multiply(BigDecimal.valueOf(100)).divide(stdHours, 1, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            Map<String, Object> row = new LinkedHashMap<>();
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
            result.add(row);
        }
        return result;
    }

    private void fillNames(List<Schedule> list) {
        // 填充项目名称
        List<Long> projectIds = list.stream()
                .map(Schedule::getProjectId).filter(java.util.Objects::nonNull).distinct().toList();
        if (!projectIds.isEmpty()) {
            Map<Long, Project> projectMap = projectMapper.selectBatchIds(projectIds).stream()
                    .collect(Collectors.toMap(Project::getId, p -> p));
            list.forEach(s -> {
                Project p = projectMap.get(s.getProjectId());
                if (p != null) s.setProjectName(p.getName());
            });
        }
        // 填充日程归属成员姓名
        List<Long> userIds = list.stream()
                .map(Schedule::getUserId).filter(java.util.Objects::nonNull).distinct().toList();
        if (!userIds.isEmpty()) {
            Map<Long, String> nameMap = memberNames(userIds);
            list.forEach(s -> s.setCreatorName(nameMap.get(s.getUserId())));
        }
        // 填充预约设备名称
        List<Long> resourceIds = list.stream()
                .map(Schedule::getResourceId).filter(java.util.Objects::nonNull).distinct().toList();
        if (!resourceIds.isEmpty()) {
            Map<Long, String> resourceMap = scheduleResourceMapper.selectBatchIds(resourceIds).stream()
                    .collect(Collectors.toMap(ScheduleResource::getId, ScheduleResource::getName));
            list.forEach(s -> s.setResourceName(resourceMap.get(s.getResourceId())));
        }
    }

    /** 批量查询用户 ID → 显示姓名（昵称为空时回退登录账号） */
    private Map<Long, String> memberNames(List<Long> userIds) {
        List<Long> ids = userIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return sysUserMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId,
                        u -> StringUtils.hasText(u.getNickname()) ? u.getNickname() : u.getUsername()));
    }

    private Long requireValidResource(Long resourceId) {
        if (resourceId == null) return null;
        ScheduleResource resource = scheduleResourceMapper.selectById(resourceId);
        if (resource == null || resource.getStatus() == null || resource.getStatus() != 1) {
            throw new BusinessException("预约设备不存在或已停用");
        }
        return resource.getId();
    }

    private void requireValidProject(Long projectId) {
        if (projectId == null) return;
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("关联项目不存在");
        }
    }

    private void copyFields(ScheduleRequest request, Schedule schedule) {
        schedule.setProjectId(request.getProjectId());
        schedule.setResourceId(requireValidResource(request.getResourceId()));
        schedule.setTitle(StringUtils.hasText(request.getTitle()) ? request.getTitle() : null);
        schedule.setDescription(request.getDescription());
        schedule.setScheduleDate(request.getScheduleDate());
        schedule.setEndDate(request.getEndDate());
        schedule.setStartTime(request.getStartTime());
        schedule.setEndTime(request.getEndTime());
        schedule.setHours(request.getHours() != null ? request.getHours() : java.math.BigDecimal.ZERO);
        schedule.setType(request.getType());
    }
}
