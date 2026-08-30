package com.accounting.firm.invoice.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 发票核销汇总视图对象（发票金额 vs 已收核销）
 */
@Data
public class InvoiceSummaryVO {

    private Long invoiceId;

    private String invoiceNo;

    /** 增值税专用发票 / 增值税普通发票 */
    private String type;

    private String contractNo;

    private String contractName;

    private String clientName;

    /** 发票金额（元） */
    private BigDecimal invoiceAmount;

    /** 已收核销合计（元） */
    private BigDecimal collectedAmount;

    /** 未核销余额（元）＝发票金额 − 已收核销，可为负（超收） */
    private BigDecimal outstanding;

    /** 核销进度百分比（0–100+，四舍五入到整数） */
    private Integer progressPercent;

    public void fillDerived() {
        if (outstanding == null) {
            outstanding = invoiceAmount.subtract(collectedAmount);
        }
        progressPercent = com.accounting.firm.collection.dto.CollectionSummaryVO.SummaryCalculator
                .percent(collectedAmount, invoiceAmount);
    }
}
