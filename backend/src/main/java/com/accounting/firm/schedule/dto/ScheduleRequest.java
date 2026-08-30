package com.accounting.firm.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ScheduleRequest {

    private Long projectId;

    /** 日程所属用户 ID 列表（支持多选，为每位成员创建日程） */
    private java.util.List<Long> userIds;

    /** 日程标题（可选；未填时统计与色带按 无标题 处理） */
    @Size(max = 200, message = "日程标题长度不能超过 200")
    private String title;

    private String description;

    @NotNull(message = "日期不能为空")
    private LocalDate scheduleDate;

    /** 结束日期（跨天日程，可选） */
    private LocalDate endDate;

    /** 开始时间 HH:mm（可选） */
    private String startTime;

    /** 结束时间 HH:mm（可选） */
    private String endTime;

    /** 工时（仅作展示参考；统计工时按 7 小时/天 + 时间净工时 + 加班规则自动推算，与该字段无关） */
    @PositiveOrZero(message = "工时不能为负数")
    private BigDecimal hours;

    /** work/leave/travel/other */
    @NotBlank(message = "类型不能为空")
    private String type;
}
