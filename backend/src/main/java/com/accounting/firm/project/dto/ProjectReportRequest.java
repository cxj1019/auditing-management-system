package com.accounting.firm.project.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 项目报告登记请求（审计报告台账）
 */
@Data
public class ProjectReportRequest {

    /** 报告文号（传 null 清空） */
    private String reportNo;

    /** 报告出具日期 */
    private LocalDate reportDate;

    /** 签发合伙人 */
    private String reportPartnerName;

    /** 报告备注 */
    private String reportRemark;
}
