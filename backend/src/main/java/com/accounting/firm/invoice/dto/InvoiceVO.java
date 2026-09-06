package com.accounting.firm.invoice.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 发票视图对象（带出合同/项目/客户与核销信息）
 */
@Data
public class InvoiceVO {

    private Long id;

    private String invoiceNo;

    private Long contractId;

    /** 合同字号 */
    private String contractNo;

    /** 合同名称 */
    private String contractName;

    /** 所属项目编号 */
    private String projectNo;

    /** 所属项目名称 */
    private String projectName;

    private Long clientId;

    /** 客户名称 */
    private String clientName;

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
    /** 不含税金额（元），未填时按价税合计与税率推算 */
    private BigDecimal amountExTax;

    /** 税额（元），未填时按价税合计 − 不含税金额推算 */
    private BigDecimal taxAmount;


    private LocalDate invoiceDate;

    /** 0-待开票 1-已开票 2-已作废 */
    private Integer status;

    /** 垫付开票：向客户收取的代垫费用 */
    private Boolean isRecharge;

    /** 发票品名（按业务类型字典自动带出） */
    private String invoiceItem;

    /** 税收编码 */
    private String taxCode;

    /** 税收分类 */
    private String taxClass;

    private String remark;

    /** 已收核销金额（元） */
    private BigDecimal collectedAmount;

    private LocalDateTime createTime;
}
