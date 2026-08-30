package com.accounting.firm.reimbursement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 报销单创建/编辑请求（草稿）
 */
@Data
public class ReimbursementRequest {

    /** 关联项目 ID（可选；编辑时忽略） */
    @Positive(message = "项目 ID 不合法")
    private Long projectId;

    /** 报销单标题 */
    @NotBlank(message = "报销单标题不能为空")
    @Size(max = 200, message = "报销单标题长度不能超过 200")
    private String title;

    /** 费用明细行（提交前至少一条） */
    @Valid
    private List<ReimbursementItemRequest> items;
}
