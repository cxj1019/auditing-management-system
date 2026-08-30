package com.accounting.firm.collection.entity;

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
 * 收款记录实体
 */
@Data
@TableName("contract_payment")
public class ContractPayment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联合同 ID（登记时由发票带出；历史数据直接挂合同） */
    private Long contractId;

    /** 关联发票 ID（收款核销到发票；历史数据为空） */
    private Long invoiceId;

    /** 收款金额（元），必须大于 0 */
    private BigDecimal amount;

    /** 收款日期 */
    private LocalDate paymentDate;

    /** 收款方式：转账/现金/支票/其他 */
    private String paymentMethod;

    /** 付款方 */
    private String payerName;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
