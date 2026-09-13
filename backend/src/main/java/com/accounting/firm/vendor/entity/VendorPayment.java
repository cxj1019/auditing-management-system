package com.accounting.firm.vendor.entity;

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
 * 对公付款：向供应商等外部单位支付款项，可归集项目，含发票号与审批流
 * 状态：0-草稿 1-待审批 2-已批准 3-已驳回 4-已付款
 */
@Data
@TableName("vendor_payment")
public class VendorPayment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 付款编号（FK+年份+流水） */
    private String paymentNo;

    /** 供应商名称 */
    private String vendorName;

    /** 付款事由/摘要 */
    private String summary;

    /** 归集项目（可空=公司层面支出） */
    private Long projectId;

    /** 关联合同（可选） */
    private Long contractId;

    /** 价税合计 */
    private BigDecimal amount;

    /** 税率（%） */
    private BigDecimal taxRate;

    /** 税额 */
    private BigDecimal taxAmount;

    /** 不含税金额（成本口径） */
    private BigDecimal amountExTax;

    private LocalDate paymentDate;

    /** 付款方式：转账/现金/支票/其他 */
    private String paymentMethod;

    /** 供应商发票号（可后补） */
    private String invoiceNo;

    /** 关联进项发票 ID（NULL = 预付，尚未取得发票） */
    private Long vendorInvoiceId;

    private Integer status;

    private String approverName;

    private String approveComment;

    private LocalDateTime approveTime;

    private String paidBy;

    private LocalDateTime paidTime;

    private String createBy;

    /** 登记人姓名快照 */
    private String creatorName;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;

    /** 非数据库字段：项目名称 */
    @TableField(exist = false)
    /** 非数据库字段：项目名称 */
    private String projectName;

    /** 非数据库字段：已核销金额（同一进项发票的付款合计，列表展示用，付款场景不填） */
    private java.math.BigDecimal paidAmount;

    /** 非数据库字段：合同编号 */
    @TableField(exist = false)
    private String contractNo;
}
