package com.accounting.firm.schedule;

import com.accounting.firm.schedule.entity.Schedule;
import com.accounting.firm.schedule.service.ScheduleHoursCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 日程工时推算规则单元测试
 * <p>规则：工作时段 9:00–17:00（午休 12:00–13:00 不计），全天 7 小时；
 * 加班每满 4 小时强制休息 1 小时（不计工时）。</p>
 */
class ScheduleHoursCalculatorTest {

    private static final LocalDate D1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate D3 = LocalDate.of(2026, 9, 3);

    private static Schedule schedule(LocalDate start, LocalDate end, String startTime, String endTime) {
        Schedule s = new Schedule();
        s.setScheduleDate(start);
        s.setEndDate(end);
        s.setStartTime(startTime);
        s.setEndTime(endTime);
        return s;
    }

    // ---------- 普通日程 ----------

    @Test
    void singleDayWithoutTimeIsSevenHours() {
        assertEquals(0, BigDecimal.valueOf(7).compareTo(
                ScheduleHoursCalculator.of(D1, null, null, null)));
        assertEquals(0, BigDecimal.valueOf(7).compareTo(
                ScheduleHoursCalculator.of(D1, D1, null, null)));
    }

    @Test
    void singleDayWithTimeUsesTimeDifference() {
        // 9:00–12:00 → 3 小时
        assertEquals(0, BigDecimal.valueOf(3).compareTo(
                ScheduleHoursCalculator.of(D1, D1, "09:00", "12:00")));
        // 14:00–15:00 → 1 小时
        assertEquals(0, BigDecimal.valueOf(1).compareTo(
                ScheduleHoursCalculator.of(D1, D1, "14:00", "15:00")));
    }

    @Test
    void timeOutsideWorkWindowIsClamped() {
        // 8:00–18:00 → 按 9:00–17:00 计 7 小时
        assertEquals(0, BigDecimal.valueOf(7).compareTo(
                ScheduleHoursCalculator.of(D1, D1, "08:00", "18:00")));
        // 10:00–14:00 → 跨午休扣 1 小时 → 3 小时
        assertEquals(0, BigDecimal.valueOf(3).compareTo(
                ScheduleHoursCalculator.of(D1, D1, "10:00", "14:00")));
    }

    @Test
    void multiDayWithoutTimeIsSevenPerDay() {
        // 1号至3号，无时间 → 21 小时
        assertEquals(0, BigDecimal.valueOf(21).compareTo(
                ScheduleHoursCalculator.of(D1, D3, null, null)));
    }

    @Test
    void multiDayWithTimesIsFirstFullLast() {
        // 1号9:00 至 3号12:00 → 7 + 7 + 3 = 17 小时
        assertEquals(0, BigDecimal.valueOf(17).compareTo(
                ScheduleHoursCalculator.of(D1, D3, "09:00", "12:00")));
    }

    @Test
    void invalidOrNullReturnsZero() {
        assertTrue(ScheduleHoursCalculator.of(null, null, null, null).signum() == 0);
        // 结束早于开始（16:00 开始 10:00 结束 → 与工作时段交集为空）
        assertEquals(0, ScheduleHoursCalculator.of(D1, D1, "16:00", "10:00").signum());
    }

    // ---------- 加班日程 ----------

    private static Schedule overtime(String startTime, String endTime, Double hours) {
        Schedule s = new Schedule();
        s.setScheduleDate(D1);
        s.setType(ScheduleHoursCalculator.TYPE_OVERTIME);
        s.setStartTime(startTime);
        s.setEndTime(endTime);
        if (hours != null) {
            s.setHours(BigDecimal.valueOf(hours));
        }
        return s;
    }

    @Test
    void overtimeFourHoursNoRest() {
        // 18:00–22:00 = 4 小时，未满 4 小时块不插入休息 → 计 4 小时
        assertEquals(0, BigDecimal.valueOf(4).compareTo(
                ScheduleHoursCalculator.effectiveHours(overtime("18:00", "22:00", null))));
    }

    @Test
    void overtimeFiveHoursDeductsOneRest() {
        // 18:00–23:00 = 5 小时 → 4 工时 + 1 强制休息 → 计 4 小时
        assertEquals(0, BigDecimal.valueOf(4).compareTo(
                ScheduleHoursCalculator.effectiveHours(overtime("18:00", "23:00", null))));
    }

    @Test
    void overtimeEightHoursDeductsOneRest() {
        // 8 小时连续加班 → 4 + 1(休) + 3 → 计 7 小时
        assertEquals(0, BigDecimal.valueOf(7).compareTo(
                ScheduleHoursCalculator.effectiveHours(overtime("18:00", "02:00", 8.0))));
    }

    @Test
    void overtimeNineHoursDeductsTwoRests() {
        // 9 小时 → 4 + 1 + 4 = 8 小时工时（中途强制休息 1 小时不计）
        Schedule s = overtime("18:00", "03:00", 9.0);
        assertEquals(0, BigDecimal.valueOf(8).compareTo(
                ScheduleHoursCalculator.effectiveHours(s)));
    }

    @Test
    void overtimeWithoutTimeUsesManualHours() {
        // 无时间，手填 5 小时 → 扣 1 次强制休息 → 计 4 小时
        assertEquals(0, BigDecimal.valueOf(4).compareTo(
                ScheduleHoursCalculator.effectiveHours(overtime(null, null, 5.0))));
    }

    @Test
    void overtimeUnderFourHoursCountsFully() {
        // 2 小时加班不满一块，无休息 → 计 2 小时
        assertEquals(0, BigDecimal.valueOf(2).compareTo(
                ScheduleHoursCalculator.effectiveHours(overtime("18:00", "20:00", null))));
    }
}
