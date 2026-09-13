package com.accounting.firm.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 月度工时锁定：锁定的月份（YYYY-MM）内日程不可增删改
 */
@Data
@TableName("schedule_lock")
public class ScheduleLock implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 锁定月份，格式 YYYY-MM */
    private String lockMonth;

    private String lockedBy;

    private LocalDateTime createTime;
}
