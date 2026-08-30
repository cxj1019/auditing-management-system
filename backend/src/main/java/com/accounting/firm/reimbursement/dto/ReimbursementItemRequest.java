package com.accounting.firm.reimbursement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 费用明细行请求
 */
@Data
public class ReimbursementItemRequest {

    /** 明细行 ID（编辑草稿时已保存的行携带，新行不传） */
    private Long id;

    @NotBlank(message = "费用类别不能为空")
    @Size(max = 30, message = "费用类别长度不能超过 30")
    private String category;

    @NotNull(message = "明细金额不能为空")
    @Positive(message = "明细金额必须大于 0")
    private BigDecimal amount;

    @NotNull(message = "费用日期不能为空")
    private LocalDate expenseDate;

    @Size(max = 300, message = "事由说明长度不能超过 300")
    private String description;

    /** 发票号（可选） */
    @Size(max = 50, message = "发票号长度不能超过 50")
    private String invoiceNumber;

    /** 是否增值税发票 */
    private Boolean isVatInvoice = false;
}
