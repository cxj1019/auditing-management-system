package com.accounting.firm.invoice.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 应收账龄视图对象：已开票未全额回款的发票
 */
@Data
public class InvoiceAgingVO {

    private Long invoiceId;

    private String invoiceNo;

    private String clientName;

    private String contractNo;

    private String projectName;

    /** 开票日期（可空，空则不参与账龄计算） */
    private LocalDate invoiceDate;

    /** 账龄天数（距开票日期） */
    private Integer agingDays;

    /** 账龄段：0-30 / 31-60 / 61-90 / 90+ */
    private String bucket;

    /** 发票金额（含税） */
    private BigDecimal amount;

    /** 已核销金额 */
    private BigDecimal collectedAmount;

    /** 未收余额 */
    private BigDecimal outstanding;
}
