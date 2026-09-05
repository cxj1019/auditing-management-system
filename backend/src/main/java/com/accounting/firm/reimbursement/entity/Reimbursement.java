package com.accounting.firm.reimbursement.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 报销单实体
 */
@Data
@TableName("reimbursement")
public class Reimbursement implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 报销编号：BX + yyyyMMdd + 4 位流水，全局唯一 */
    private String reimbursementNo;

    /** 申请人用户 ID（归属判断以此为准，用户名仅作展示与兜底） */
    private Long applicantId;

    /** 申请人账号 */
    private String applicantUsername;

    /** 申请人姓名 */
    private String applicantName;

    /** 关联项目 ID（可空，用于项目成本归集） */
    private Long projectId;

    /** 报销单标题 */
    private String title;

    /** 单据总金额（元），由明细行合计自动维护 */
    private BigDecimal totalAmount;

    /** 状态：0-草稿 1-待审批 2-已批准 3-已驳回 4-待终审 */
    private Integer status;

    /** 一级审批人姓名（转终审时记录） */
    private String primaryApproverName;

    /** 财务标记：已收发票 */
    private Boolean isInvoiceReceived;

    /** 财务标记：已付款 */
    private Boolean isPaid;

    /** 审批人账号 */
    private String approverUsername;

    /** 审批人姓名 */
    private String approverName;

    /** 审批时间 */
    private LocalDateTime approveTime;

    /** 审批意见 */
    private String approveComment;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
