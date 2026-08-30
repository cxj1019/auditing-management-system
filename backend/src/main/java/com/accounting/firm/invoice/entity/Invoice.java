package com.accounting.firm.invoice.entity;

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
 * 发票实体
 * <p>业务流：签合同后开发票，收款核销到发票。客户 ID 冗余自合同所属项目。</p>
 */
@Data
@TableName("invoice")
public class Invoice implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发票号码（人工填写，全局唯一） */
    private String invoiceNo;

    /** 挂靠合同 ID */
    private Long contractId;

    /** 客户 ID（冗余自项目） */
    private Long clientId;

    /** 增值税专用发票 / 增值税普通发票 */
    private String type;

    /** 价税合计（元） */
    private BigDecimal amount;

    /** 币种：人民币/美元/日元/欧元/港币/英镑 */
    private String currency;

    /** 外币金额（币种非人民币时） */
    private BigDecimal foreignAmount;

    /** 中国银行牌价（每 100 外币兑人民币） */
    private BigDecimal exchangeRate;

    /** 牌价发布时间（留存备查） */
    private String ratePublishTime;


    /** 税率（%） */
    private BigDecimal taxRate;

    /** 开票日期 */
    private LocalDate invoiceDate;

    /** 发票品名（按业务类型字典自动带出） */
    private String invoiceItem;

    /** 税收编码 */
    private String taxCode;

    /** 税收分类 */
    private String taxClass;

    /** 不含税金额（元），未填时按价税合计与税率推算 */
    private BigDecimal amountExTax;

    /** 税额（元），未填时按价税合计 − 不含税金额推算 */
    private BigDecimal taxAmount;

    /** 0-待开票 1-已开票 2-已作废 */
    private Integer status;

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
