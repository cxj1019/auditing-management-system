package com.accounting.firm.contract.dto;

import com.accounting.firm.contract.entity.Contract;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 合同视图对象（带出所属项目信息）
 */
@Data
public class ContractVO {

    private Long id;

    /** 所属项目 ID */
    private Long projectId;

    /** 项目编号 */
    private String projectNo;

    /** 项目名称 */
    private String projectName;

    private String contractNo;

    private String name;

    /** 客户名称（来自所属项目，只读） */
    private String clientName;

    private String contractType;

    /** 业务类型 */
    private String bizType;

    private BigDecimal amount;
    /** 税率（%，可空） */
    private BigDecimal taxRate;
    /** 不含税金额（元，可空） */
    private BigDecimal amountExTax;
    /** 税额（元，可空） */
    private BigDecimal taxAmount;
    /** 币种 */
    private String currency;

    /** 外币金额 */
    private BigDecimal foreignAmount;

    /** 中国银行牌价（每 100 外币兑人民币） */
    private BigDecimal exchangeRate;

    /** 牌价发布时间 */
    private String ratePublishTime;

    private LocalDate signDate;

    private LocalDate serviceStart;

    private LocalDate serviceEnd;

    /** 合同保管人 */
    private String keeperName;

    private Integer status;

    private String remark;

    private LocalDateTime createTime;

    public static ContractVO from(Contract c) {
        ContractVO vo = new ContractVO();
        vo.setId(c.getId());
        vo.setProjectId(c.getProjectId());
        vo.setContractNo(c.getContractNo());
        vo.setName(c.getName());
        vo.setContractType(c.getContractType());
        vo.setBizType(c.getBizType());
        vo.setAmount(c.getAmount());
        vo.setTaxRate(c.getTaxRate());
        vo.setAmountExTax(c.getAmountExTax());
        vo.setTaxAmount(c.getTaxAmount());
        vo.setCurrency(c.getCurrency());
        vo.setForeignAmount(c.getForeignAmount());
        vo.setExchangeRate(c.getExchangeRate());
        vo.setRatePublishTime(c.getRatePublishTime());
        vo.setSignDate(c.getSignDate());
        vo.setServiceStart(c.getServiceStart());
        vo.setServiceEnd(c.getServiceEnd());
        vo.setKeeperName(c.getKeeperName());
        vo.setStatus(c.getStatus());
        vo.setRemark(c.getRemark());
        vo.setCreateTime(c.getCreateTime());
        return vo;
    }
}
