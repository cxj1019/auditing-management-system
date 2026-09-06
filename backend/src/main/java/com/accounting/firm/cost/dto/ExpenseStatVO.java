package com.accounting.firm.cost.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 员工费用统计行：申请人 × 费用类别 的汇总
 */
@Data
public class ExpenseStatVO {

    /** 申请人姓名 */
    private String applicantName;

    /** 费用类别 */
    private String category;

    /** 合计金额（元） */
    private BigDecimal total;

    /** 笔数 */
    private Long cnt;
}
