package com.accounting.firm.collection.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 合同收款汇总视图对象
 */
@Data
public class CollectionSummaryVO {

    private Long contractId;

    private String contractNo;

    private String contractName;

    private String clientName;

    /** 合同金额（元） */
    private BigDecimal contractAmount;

    /** 已收合计（元） */
    private BigDecimal totalCollected;

    /** 未收余额（元）＝合同金额 − 已收合计，可为负（超收） */
    private BigDecimal outstanding;

    /** 收款进度百分比（0–100+，四舍五入到整数） */
    private Integer progressPercent;

    public void fillDerived() {
        if (outstanding == null) {
            outstanding = contractAmount.subtract(totalCollected);
        }
        progressPercent = SummaryCalculator.percent(totalCollected, contractAmount);
    }

    /**
     * 进度计算纯函数：percent = collected / amount * 100，四舍五入取整；
     * 合同金额为 0 或 null 时进度记 0，避免除零
     */
    public static final class SummaryCalculator {

        private SummaryCalculator() {
        }

        public static int percent(BigDecimal collected, BigDecimal contractAmount) {
            if (contractAmount == null || contractAmount.signum() == 0) {
                return 0;
            }
            return collected.multiply(BigDecimal.valueOf(100))
                    .divide(contractAmount, 0, java.math.RoundingMode.HALF_UP)
                    .intValue();
        }
    }
}
