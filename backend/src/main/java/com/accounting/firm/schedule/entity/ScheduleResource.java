package com.accounting.firm.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 日程可选设备（会议室/公司车辆等）
 */
@Data
@TableName("schedule_resource")
public class ScheduleResource implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 设备名称，如 大会议室 / 公司车辆 */
    private String name;

    /** 设备类型：会议室 / 车辆 / 其他 */
    private String resourceType;

    /** 1-启用 0-停用 */
    private Integer status;

    private LocalDateTime createTime;
}
