package com.accounting.firm.contract.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 合同下拉选项（供发票登记选择，带出项目/客户与客户开票信息）
 */
@Data
public class ContractOptionVO {

    private Long id;

    private String contractNo;

    private String name;

    private String contractType;

    private BigDecimal amount;

    /** 所属项目 */
    private String projectName;

    /** 所属客户 */
    private Long clientId;

    private String clientName;

    /** 业务类型（决定字号与开票要素） */
    private String bizType;

    /** 开票要素（按业务类型字典带出） */
    private String invoiceItem;

    private String taxCode;

    private String taxClass;

    /** 客户类型：domestic=境内 overseas=境外（境外默认外币开票） */
    private String clientType;

    /** 客户开票信息（登记发票时校对） */
    private String invoiceTitle;

    private String invoiceTaxNo;

    private String invoiceBankName;

    private String invoiceBankAccount;

    private String invoiceAddress;

    private String invoicePhone;
}
