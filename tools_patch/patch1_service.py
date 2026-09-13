import io
p = r'backend/src/main/java/com/accounting/firm/schedule/service/ScheduleService.java'
s = io.open(p, encoding='utf-8').read()
if 'hoursMatrix' not in s:
    s = s.replace(
"""    /** 可选设备清单（会议室/公司车辆等，启用状态） */
    List<ScheduleResource> listResources();""",
"""    /** 可选设备清单（会议室/公司车辆等，启用状态） */
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
    void unlockMonth(String month, SecurityUser currentUser);""")
    io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('service interface ok')
