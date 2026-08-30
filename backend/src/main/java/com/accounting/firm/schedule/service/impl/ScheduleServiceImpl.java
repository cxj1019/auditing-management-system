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
import com.accounting.firm.schedule.mapper.ScheduleMapper;
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
    private final DataScopeService dataScopeService;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createSchedule(ScheduleRequest request, SecurityUser currentUser) {
        requireValidProject(request.getProjectId());
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
        for (Schedule s : schedules) {
            hoursByUser.merge(s.getUserId(), ScheduleHoursCalculator.effectiveHours(s), BigDecimal::add);
        }
        Map<Long, String> nameByUser = memberNames(hoursByUser.keySet().stream().toList());
        List<Map<String, Object>> result = new ArrayList<>();
        for (var entry : hoursByUser.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", entry.getKey());
            row.put("memberName", nameByUser.getOrDefault(entry.getKey(), ""));
            row.put("totalHours", entry.getValue());
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

    private void requireValidProject(Long projectId) {
        if (projectId == null) return;
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("关联项目不存在");
        }
    }

    private void copyFields(ScheduleRequest request, Schedule schedule) {
        schedule.setProjectId(request.getProjectId());
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
