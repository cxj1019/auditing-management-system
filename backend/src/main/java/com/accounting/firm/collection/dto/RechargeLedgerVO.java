package com.accounting.firm.collection.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 垫付台账视图对象（按项目归集代垫费用闭环：垫付 → 开票 → 收回）
 */
@Data
public class RechargeLedgerVO {

    private Long projectId;

    private String projectNo;

    private String projectName;

    private String clientName;

    /** 垫付总额：已批准报销中勾选"可向客户收取"的明细合计（含税） */
    private BigDecimal rechargeTotal;

    /** 已开票：该项目下标记为垫付开票的已开票发票合计（含税，不含已作废） */
    private BigDecimal invoicedTotal;

    /** 已收回：上述垫付发票的已核销回款合计 */
    private BigDecimal collectedTotal;

    /** 待开票 = 垫付总额 − 已开票（可为负，表示开票超出垫付） */
    private BigDecimal pendingInvoice;

    /** 待收回 = 已开票 − 已收回（可为负，表示超收） */
    private BigDecimal pendingCollect;

    /** 闭环状态：pending-invoice-待开票 / pending-collect-待收回 / settled-已结清 */
    private String status;

    public void fillDerived() {
        pendingInvoice = nvl(rechargeTotal).subtract(nvl(invoicedTotal));
        pendingCollect = nvl(invoicedTotal).subtract(nvl(collectedTotal));
        if (pendingCollect.signum() > 0) {
            status = "pending-collect";
        } else if (pendingInvoice.signum() > 0) {
            status = "pending-invoice";
        } else {
            status = "settled";
        }
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
