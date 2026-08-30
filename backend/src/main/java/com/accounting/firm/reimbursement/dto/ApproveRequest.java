package com.accounting.firm.reimbursement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 报销审批请求
 */
@Data
public class ApproveRequest {

    /** 审批动作：approve-批准 reject-驳回 */
    @NotBlank(message = "审批动作不能为空")
    private String action;

    /** 审批意见（必填） */
    @NotBlank(message = "审批意见不能为空")
    @Size(max = 300, message = "审批意见长度不能超过 300")
    private String comment;
}
