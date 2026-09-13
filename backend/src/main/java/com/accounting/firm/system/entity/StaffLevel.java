package com.accounting.firm.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 员工级别（A1/A2/A3/S1/S2/S3/项目经理/经理/合伙人/其他）及标准工时单价
 */
@Data
@TableName("staff_level")
public class StaffLevel implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private BigDecimal hourlyRate;

    private Integer sort;
}
