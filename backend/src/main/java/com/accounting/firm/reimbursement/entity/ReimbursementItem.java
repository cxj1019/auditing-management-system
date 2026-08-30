package com.accounting.firm.reimbursement.entity;

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
 * 报销费用明细行实体
 */
@Data
@TableName("reimbursement_item")
public class ReimbursementItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属报销单 ID */
    private Long reimbursementId;

    /** 费用类别：差旅费/交通费/办公费/餐饮费/其他 */
    private String category;

    /** 金额（元） */
    private BigDecimal amount;

    /** 费用日期 */
    private LocalDate expenseDate;

    /** 事由说明 */
    private String description;

    /** 发票号（可选） */
    private String invoiceNumber;

    /** 是否增值税发票 */
    private Boolean isVatInvoice;

    private LocalDateTime createTime;
}
