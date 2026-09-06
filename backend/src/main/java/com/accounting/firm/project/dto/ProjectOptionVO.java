package com.accounting.firm.project.dto;

import lombok.Data;

/**
 * 项目下拉选项视图对象：仅含关联所需的最小字段
 */
@Data
public class ProjectOptionVO {

    private Long id;

    private String projectNo;

    private String name;

    /** 业务类型（合同登记时默认带出项目的业务类型） */
    private String bizType;

    /** 客户名称（可空，用于辅助识别） */
    private String clientName;
}
