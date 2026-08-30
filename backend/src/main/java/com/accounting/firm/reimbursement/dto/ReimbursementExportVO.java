package com.accounting.firm.reimbursement.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 报销费用明细导出行（扁平结构，供 Excel 导出）
 */
@Data
public class ReimbursementExportVO {

    private String reimbursementNo;

    private String applicantName;

    private String projectName;

    private String title;

    private String itemCategory;

    private BigDecimal itemAmount;

    private LocalDate itemExpenseDate;

    private String itemDescription;

    private String invoiceNumber;

    private Boolean isVatInvoice;

    /** 单据状态文本 */
    private String statusLabel;

    private String approverName;
}
