package com.accounting.firm.schedule.service;

import com.accounting.firm.common.api.PageResult;
import com.accounting.firm.common.security.SecurityUser;
import com.accounting.firm.schedule.dto.ScheduleRequest;
import com.accounting.firm.schedule.entity.Schedule;
import com.accounting.firm.schedule.entity.ScheduleResource;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface ScheduleService extends IService<Schedule> {

    /** 按日期范围查询日程（当前用户或全部） */
    /** 可选设备清单（会议室/公司车辆等，启用状态） */
    List<ScheduleResource> listResources();

    /** 人 × 项目 工时矩阵（部门范围内，指定日期区间），行：userId/userName/projectId/projectName/hours */
    List<java.util.Map<String, Object>> hoursMatrix(LocalDate startDate, LocalDate endDate);

    /** 经理确认成员时段工时，返回确认条数 */
    int confirmHours(LocalDate startDate, LocalDate endDate, Long userId, SecurityUser currentUser);

    /** 已锁定的月份清单（YYYY-MM，升序） */
    List<String> listLocks();

    /** 锁定月份（仅管理员） */
    void lockMonth(String month, SecurityUser currentUser);

    /** 解锁月份（仅管理员） */
    void unlockMonth(String month, SecurityUser currentUser);

    List<Schedule> listByDateRange(LocalDate startDate, LocalDate endDate, Long projectId, Long userId);

    /** 创建日程 */
    void createSchedule(ScheduleRequest request, SecurityUser currentUser);

    /** 更新日程（所有人都可以修改） */
    void updateSchedule(Long id, ScheduleRequest request, SecurityUser currentUser);

    /** 删除整个日程（含全部参与人员），所有人可操作 */
    void deleteEvent(Long id);

    /** 退出日程：仅移除指定参与人员自己的这条 */
    void exitEvent(Long id);

    /** 工时汇总（按成员统计） */
    List<Map<String, Object>> hoursSummary(LocalDate startDate, LocalDate endDate);
}
