package com.accounting.firm.cost.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 人工成本登记/编辑请求
 */
@Data
public class LaborCostRequest {

    /** 所属项目 ID（必填） */
    @NotNull(message = "所属项目不能为空")
    @Positive(message = "项目 ID 不合法")
    private Long projectId;

    @NotBlank(message = "人员姓名不能为空")
    @Size(max = 50, message = "人员姓名长度不能超过 50")
    private String personName;

    /** 成本月份，格式 YYYY-MM */
    @NotBlank(message = "成本月份不能为空")
    @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "成本月份格式应为 YYYY-MM")
    private String costMonth;

    @NotNull(message = "成本金额不能为空")
    @Positive(message = "成本金额必须大于 0")
    private BigDecimal amount;

    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;
}
