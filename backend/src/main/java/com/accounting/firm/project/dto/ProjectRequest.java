package com.accounting.firm.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 项目登记/编辑请求
 */
@Data
public class ProjectRequest {

    /** 项目 ID（编辑时必填） */
    private Long id;

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 200, message = "项目名称长度不能超过 200")
    private String name;

    @NotBlank(message = "项目类型不能为空")
    @Size(max = 30, message = "项目类型长度不能超过 30")
    private String type;

    /** 项目性质：收入型 / 无收入型（来自业务类型字典） */
    @Size(max = 20, message = "项目性质长度不能超过 20")
    private String bizNature;

    /** 业务类型（来自业务类型字典） */
    @Size(max = 100, message = "业务类型长度不能超过 100")
    private String bizType;

    /** 关联客户 ID（必填，从客户管理模块选择） */
    @NotNull(message = "客户不能为空")
    private Long clientId;

    /** 归属部门 ID（必填，决定项目相关数据对哪个部门可见可编辑） */
    @NotNull(message = "归属部门不能为空")
    private Long deptId;

    @NotBlank(message = "项目合伙人不能为空")
    @Size(max = 50, message = "项目合伙人长度不能超过 50")
    private String partnerName;

    @NotBlank(message = "项目经理不能为空")
    @Size(max = 50, message = "项目经理长度不能超过 50")
    private String managerName;

    @NotBlank(message = "项目现场负责人不能为空")
    @Size(max = 50, message = "项目现场负责人长度不能超过 50")
    private String siteLeaderName;

    /** 项目期间起止（可选；两者都填时校验先后顺序） */
    private LocalDate startDate;

    private LocalDate endDate;

    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;
}
