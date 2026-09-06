package com.accounting.firm.client.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户对账单视图对象：按客户归集合约、发票与回款
 */
@Data
public class ClientStatementVO {

    private String clientNo;

    private String clientName;

    private String clientType;

    /** 合同总额（元） */
    private BigDecimal contractTotal;

    /** 已开票总额（元，含税，不含已作废） */
    private BigDecimal invoiceIssuedTotal;

    /** 已回款总额（元） */
    private BigDecimal collectedTotal;

    /** 未收余额 = 已开票 − 已回款 */
    private BigDecimal outstanding;

    private List<ContractRow> contracts;

    private List<InvoiceRow> invoices;

    private List<PaymentRow> payments;

    @Data
    public static class ContractRow {
        private String contractNo;
        private String name;
        private String projectName;
        private BigDecimal amount;
        private String statusLabel;
    }

    @Data
    public static class InvoiceRow {
        private String invoiceNo;
        private String contractNo;
        private LocalDate invoiceDate;
        private BigDecimal amount;
        private BigDecimal amountExTax;
        private BigDecimal taxAmount;
        private String statusLabel;
    }

    @Data
    public static class PaymentRow {
        private LocalDate paymentDate;
        private String contractNo;
        private String invoiceNo;
        private BigDecimal amount;
        private String paymentMethod;
        private LocalDateTime createTime;
    }
}
