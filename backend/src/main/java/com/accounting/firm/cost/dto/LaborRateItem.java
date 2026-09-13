package com.accounting.firm.cost.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 工时单价保存项 */
@Data
public class LaborRateItem {

    private Long userId;

    private BigDecimal hourlyRate;
}
