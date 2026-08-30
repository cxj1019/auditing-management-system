package com.accounting.firm.invoice.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 发票下拉选项（供收款登记选择已开票发票）
 */
@Data
public class InvoiceOptionVO {

    private Long id;

    private String invoiceNo;

    private Long contractId;

    private String contractNo;

    private String contractName;

    private String clientName;

    /** 发票金额（元） */
    private BigDecimal amount;

    /** 已收核销金额（元） */
    private BigDecimal collectedAmount;
}
