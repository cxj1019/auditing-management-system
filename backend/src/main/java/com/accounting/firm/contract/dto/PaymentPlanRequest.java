package com.accounting.firm.contract.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 收款计划节点创建/编辑请求
 */
@Data
public class PaymentPlanRequest {

    /** 节点 ID（编辑时必填） */
    private Long id;

    /** 计划收款日期 */
    @NotNull(message = "计划收款日期不能为空")
    private LocalDate dueDate;

    /** 计划金额（元） */
    @NotNull(message = "计划金额不能为空")
    @Positive(message = "计划金额必须大于 0")
    private BigDecimal amount;

    @Size(max = 200, message = "备注长度不能超过 200")
    private String remark;
}
