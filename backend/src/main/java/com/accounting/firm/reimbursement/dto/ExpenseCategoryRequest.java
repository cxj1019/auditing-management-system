package com.accounting.firm.reimbursement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 报销费用类别创建/编辑请求
 */
@Data
public class ExpenseCategoryRequest {

    /** 类别 ID（编辑时必填） */
    private Long id;

    @NotBlank(message = "类别名称不能为空")
    @Size(max = 30, message = "类别名称长度不能超过 30")
    private String name;

    /** 排序号（小在前） */
    private Integer sort;

    /** 1-启用 0-停用 */
    private Integer status;
}
