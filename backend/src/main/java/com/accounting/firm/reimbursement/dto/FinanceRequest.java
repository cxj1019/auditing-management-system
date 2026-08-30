package com.accounting.firm.reimbursement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 报销财务操作请求
 */
@Data
public class FinanceRequest {

    /** 财务动作：receive-invoice-标记已收发票 mark-paid-标记已付款 */
    @NotBlank(message = "财务动作不能为空")
    private String action;
}
