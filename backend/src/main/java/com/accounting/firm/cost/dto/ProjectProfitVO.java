package com.accounting.firm.cost.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 项目利润视图对象（按项目维度聚合）
 */
@Data
public class ProjectProfitVO {

    private Long projectId;

    private String projectNo;

    private String projectName;

    private String clientName;

    /** 项目合同总额（项目下全部合同金额合计） */
    private BigDecimal contractAmount;

    /** 已收金额（项目下全部合同收款合计） */
    private BigDecimal totalCollected;

    /** 直接成本 = 已批准报销 + 人工成本 */
    private BigDecimal directCost;

    /** 其中：已批准报销合计 */
    private BigDecimal expenseCost;

    /** 其中：人工成本合计 */
    private BigDecimal laborCost;

    /** 毛利 = 已收 − 直接成本 */
    private BigDecimal grossProfit;

    /** 毛利率百分比；已收为 0 时为 null（前端显示 —） */
    private BigDecimal marginPercent;

    public void fillDerived() {
        if (directCost == null) {
            directCost = nvl(expenseCost).add(nvl(laborCost));
        }
        if (grossProfit == null) {
            grossProfit = nvl(totalCollected).subtract(directCost);
        }
        marginPercent = MarginCalculator.percent(grossProfit, totalCollected);
    }

    /**
     * 毛利率计算纯函数：margin / collected × 100，保留 2 位小数；
     * 已收为 null 或 0 时返回 null（避免除零与误导性 0%）
     */
    public static final class MarginCalculator {

        private MarginCalculator() {
        }

        public static BigDecimal percent(BigDecimal grossProfit, BigDecimal collected) {
            if (collected == null || collected.signum() == 0) {
                return null;
            }
            return grossProfit.multiply(BigDecimal.valueOf(100))
                    .divide(collected, 2, java.math.RoundingMode.HALF_UP);
        }
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
