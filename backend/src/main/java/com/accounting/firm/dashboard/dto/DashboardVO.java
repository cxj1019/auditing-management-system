package com.accounting.firm.dashboard.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 工作台聚合视图
 */
@Data
public class DashboardVO {

    private Todo todo;

    /** 本周工时（当前用户） */
    private BigDecimal weekHours;

    /** 今日日程（当前用户） */
    private List<ScheduleItem> todaySchedules;

    /** 开票与回款总览（已开票口径） */
    private Receivable receivable;

    /** 项目规模 Top5（按合同金额） */
    private List<ProjectRow> topProjects;

    @Data
    public static class Todo {
        /** 待审批报销（含待终审） */
        private long pendingReimbursement;
        /** 待开票发票 */
        private long pendingInvoice;
        /** 逾期应收（开票超期未核销完） */
        private long overdueReceivable;
        /** 逾期未回函证 */
        private long overdueConfirmation;
        /** 即将到期合同（30 天内） */
        private long expiringContract;
    }

    @Data
    public static class ScheduleItem {
        private Long id;
        private String title;
        private String type;
        private String startTime;
        private String endTime;
        private java.math.BigDecimal hours;
        private String projectName;
    }

    @Data
    public static class Receivable {
        /** 已开票合计（价税合计，未作废） */
        private BigDecimal invoicedAmount;
        /** 已核销回款合计 */
        private BigDecimal collectedAmount;
        /** 未核销余额 */
        private BigDecimal outstanding;
    }

    @Data
    public static class ProjectRow {
        private String projectNo;
        private String projectName;
        private BigDecimal contractAmount;
        private BigDecimal totalCollected;
        private Integer progressPercent;
    }
}
