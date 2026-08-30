package com.accounting.firm.schedule.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("schedule")
public class Schedule implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private Long userId;

    private String title;

    private String description;

    private LocalDate scheduleDate;

    /** 结束日期（跨天日程） */
    private LocalDate endDate;

    /** 开始时间 HH:mm（可选） */
    private String startTime;

    /** 结束时间 HH:mm（可选） */
    private String endTime;

    private BigDecimal hours;

    /** work/leave/travel/other */
    private String type;

    /** 事件分组 ID（同一批次创建的多人的日程共享，支持整场删除/单人退出） */
    private String eventId;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 非数据库字段：创建人姓名（联表填充） */
    @TableField(exist = false)
    private String creatorName;

    /** 非数据库字段：项目名称（联表填充） */
    @TableField(exist = false)
    private String projectName;
}
