package com.accounting.firm.schedule.service;

import com.accounting.firm.schedule.entity.Schedule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 日程工时推算器（纯函数，便于单元测试）
 *
 * <p>规则：工作时段 9:00–17:00，午休 12:00–13:00 不计工时，全天净工时 7 小时。</p>
 * <ul>
 *   <li>未填开始/结束时间的日程：每天按 7 小时</li>
 *   <li>有时间的按时间净工时计算（跨午休扣除 1 小时）：
 *       首日 = 开始时间 → 17:00；中间整天各 7 小时；末日 = 9:00 → 结束时间
 *       （如 1号9:00 至 3号12:00 = 7 + 7 + 3 = 17 小时）</li>
 * </ul>
 */
public final class ScheduleHoursCalculator {

    /** 日程类型：加班 */
    public static final String TYPE_OVERTIME = "加班";

    private static final int DAY_START = 9 * 60;
    private static final int DAY_END = 17 * 60;
    private static final int BREAK_START = 12 * 60;
    private static final int BREAK_END = 13 * 60;
    private static final int FULL_DAY_MINUTES = DAY_END - DAY_START - (BREAK_END - BREAK_START);

    private ScheduleHoursCalculator() {
    }

    /** 计算一条日程的工时（小时）；加班类型走加班规则 */
    public static BigDecimal effectiveHours(Schedule schedule) {
        if (TYPE_OVERTIME.equals(schedule.getType())) {
            return overtimeHours(schedule.getScheduleDate(), schedule.getStartTime(),
                    schedule.getEndTime(), schedule.getHours());
        }
        return of(schedule.getScheduleDate(), schedule.getEndDate(),
                schedule.getStartTime(), schedule.getEndTime());
    }

    /** 纯参数版本（非加班日程） */
    public static BigDecimal of(LocalDate startDate, LocalDate endDate, String startTime, String endTime) {
        if (startDate == null) {
            return BigDecimal.ZERO;
        }
        LocalDate last = endDate != null ? endDate : startDate;
        boolean hasStartTime = startTime != null && !startTime.isBlank();
        boolean hasEndTime = endTime != null && !endTime.isBlank();

        // 单日日程：按起止时间净工时计算
        if (last.equals(startDate)) {
            int from = hasStartTime ? parseMinutes(startTime) : DAY_START;
            int to = hasEndTime ? parseMinutes(endTime) : DAY_END;
            return minutesToHours(netMinutes(from, to));
        }

        // 跨天日程：首日 + 中间整天 + 末日
        long days = ChronoUnit.DAYS.between(startDate, last) + 1;
        int firstDayMinutes = hasStartTime
                ? netMinutes(parseMinutes(startTime), DAY_END)
                : FULL_DAY_MINUTES;
        int lastDayMinutes = hasEndTime
                ? netMinutes(DAY_START, parseMinutes(endTime))
                : FULL_DAY_MINUTES;
        long middleDays = Math.max(0, days - 2);
        int totalMinutes = firstDayMinutes + (int) (middleDays * FULL_DAY_MINUTES) + lastDayMinutes;
        return minutesToHours(totalMinutes);
    }

    /**
     * 加班工时：每连续加班满 4 小时强制休息 1 小时，休息时间不计工时。
     * <p>即统计工时 = 加班时段 − 强制休息时间：加班 5 小时 → 计 4 小时；
     * 加班 8 小时 → 计 7 小时；加班 9 小时（4+1+4）→ 计 8 小时。</p>
     * <p>时长取值：有开始/结束时间按时间差计算（支持跨零点）；否则取手填工时字段。</p>
     */
    static BigDecimal overtimeMinutes(long durationMinutes) {
        if (durationMinutes <= 0) {
            return BigDecimal.ZERO;
        }
        long remaining = durationMinutes;
        long work = 0;
        while (remaining > 0) {
            long chunk = Math.min(remaining, 4 * 60);
            work += chunk;
            remaining -= chunk;
            if (remaining > 0) {
                remaining -= 60; // 强制休息 1 小时，不计工时
            }
        }
        return BigDecimal.valueOf(work).divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
    }

    private static BigDecimal overtimeHours(LocalDate date, String startTime, String endTime, BigDecimal manualHours) {
        long durationMinutes;
        if (date != null && startTime != null && !startTime.isBlank()
                && endTime != null && !endTime.isBlank()) {
            int from = parseMinutes(startTime);
            int to = parseMinutes(endTime);
            durationMinutes = to >= from ? to - from : to + 24 * 60 - from; // 支持跨零点
        } else if (manualHours != null) {
            durationMinutes = manualHours.multiply(BigDecimal.valueOf(60)).longValue();
        } else {
            return BigDecimal.ZERO;
        }
        return overtimeMinutes(durationMinutes);
    }

    /** [from, to] 在工作时段 9:00–17:00 内的净分钟数（扣除午休 12:00–13:00） */
    static int netMinutes(int from, int to) {
        int fromClamped = Math.max(from, DAY_START);
        int toClamped = Math.min(to, DAY_END);
        int span = Math.max(0, toClamped - fromClamped);
        int breakOverlap = Math.max(0, Math.min(toClamped, BREAK_END) - Math.max(fromClamped, BREAK_START));
        return Math.max(0, span - breakOverlap);
    }

    /** "HH:mm" → 当日分钟数（非法输入按 9:00 计） */
    private static int parseMinutes(String time) {
        try {
            String[] parts = time.trim().split(":");
            return Integer.parseInt(parts[0]) * 60 + (parts.length > 1 ? Integer.parseInt(parts[1]) : 0);
        } catch (Exception e) {
            return DAY_START;
        }
    }

    private static BigDecimal minutesToHours(int minutes) {
        if (minutes <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
    }
}
