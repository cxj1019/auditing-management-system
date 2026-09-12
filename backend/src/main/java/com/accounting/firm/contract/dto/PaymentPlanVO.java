package com.accounting.firm.contract.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 收款计划节点视图对象（含合同已收金额对比）
 */
@Data
public class PaymentPlanVO {

    private Long id;

    private Long contractId;

    private LocalDate dueDate;

    private BigDecimal amount;

    private String remark;

    /** 该合同时点的累计已收金额（用于判断是否达成） */
    private BigDecimal contractCollected;
}
