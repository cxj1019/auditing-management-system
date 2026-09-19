package com.accounting.firm.project.dto;

import com.accounting.firm.confirmation.entity.Confirmation;
import com.accounting.firm.invoice.entity.Invoice;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 项目工作台一览 */
@Data
public class ProjectWorkbenchVO {

    private Long projectId;
    private String projectNo;
    private String projectName;
    private String clientName;
    private Integer status;
    private String statusLabel;
    private String partnerName;
    private String managerName;
    private String siteLeaderName;
    private java.time.LocalDate startDate;
    private java.time.LocalDate endDate;
    private BigDecimal budgetHours;

    /** 利润指标 */
    private BigDecimal contractAmount;
    private BigDecimal totalCollected;
    private BigDecimal expenseCost;
    private BigDecimal laborCost;
    private BigDecimal autoLaborCost;
    private BigDecimal grossProfit;
    private BigDecimal actualHours;

    /** 预算工时明细：levelName/headcount/hoursPerPerson/totalHours/actualHours */
    private List<Map<String, Object>> budgetLines;

    /** 该项目的发票（含待开票） */
    private List<Invoice> invoices;

    /** 该项目的收款记录 */
    private List<Map<String, Object>> payments;

    /** 该项目的函证 */
    private List<Confirmation> confirmations;

    /** 该项目相关的已批准报销（不含税，含行级归集） */
    private List<Map<String, Object>> reimbursements;

    /** 该项目的对公付款（已批准/已付款） */
    private List<Map<String, Object>> vendorPayments;
}
