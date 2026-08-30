package com.accounting.firm.cost.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 经营概览视图对象
 */
@Data
public class OverviewVO {

    /** 总收入（全部收款合计） */
    private BigDecimal totalIncome;

    /** 总直接成本（已批准报销 + 人工成本） */
    private BigDecimal totalCost;

    /** 总毛利 */
    private BigDecimal grossProfit;

    /** 回款率百分比 = 总收款 / 全部合同金额合计 × 100；合同总额为 0 时为 null */
    private BigDecimal collectionRate;
}
