package com.accounting.firm.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 日程节假日/调休（管理员定义；休=放假日，班=调休上班日） */
@Data
@TableName("schedule_holiday")
public class ScheduleHoliday implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 节假日/调休日期 */
    private LocalDate holidayDate;

    /** 休 / 班 */
    private String type;

    /** 名称（如 国庆节、春节调休），调休日可为空 */
    private String name;

    private String createBy;

    private LocalDateTime createTime;
}
