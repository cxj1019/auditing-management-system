package com.accounting.firm.cost.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 人员工时单价（元/小时）：工时 × 单价 = 项目人工成本（自动核算）
 */
@Data
@TableName("labor_rate")
public class LaborRate implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private BigDecimal hourlyRate;

    private String updateBy;

    private LocalDateTime updateTime;
}
