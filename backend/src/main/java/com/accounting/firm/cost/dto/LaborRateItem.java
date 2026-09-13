package com.accounting.firm.cost.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 工时单价保存项 */
@Data
public class LaborRateItem {

    private Long userId;

    private BigDecimal hourlyRate;

    /** 员工级别 ID（定级用） */
    private Long staffLevelId;
}
