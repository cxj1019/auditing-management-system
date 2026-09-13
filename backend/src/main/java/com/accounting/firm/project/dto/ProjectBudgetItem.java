package com.accounting.firm.project.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 项目预算明细项：级别 × 人数 × 每人预算工时 */
@Data
public class ProjectBudgetItem {

    private Long staffLevelId;

    private Integer headcount;

    private BigDecimal hoursPerPerson;
}
