package com.accounting.firm.collection.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 收款记录视图对象（带出关联合同信息）
 */
@Data
public class PaymentVO {

    private Long id;

    private Long contractId;

    /** 关联发票 ID */
    private Long invoiceId;

    /** 发票号码（未挂发票的历史收款为空） */
    private String invoiceNo;

    /** 合同编号 */
    private String contractNo;

    /** 合同名称 */
    private String contractName;

    /** 客户名称 */
    private String clientName;

    /** 所属项目编号 */
    private String projectNo;

    /** 所属项目名称 */
    private String projectName;

    private BigDecimal amount;

    private LocalDate paymentDate;

    private String paymentMethod;

    private String payerName;

    private String remark;

    private LocalDateTime createTime;
}
