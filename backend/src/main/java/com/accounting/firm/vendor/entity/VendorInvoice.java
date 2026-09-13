package com.accounting.firm.vendor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 进项发票（供应商发票）：登记后供对公付款核销
 */
@Data
@TableName("vendor_invoice")
public class VendorInvoice implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String vendorName;

    private String invoiceNo;

    private String type;

    private BigDecimal taxRate;

    /** 价税合计 */
    private BigDecimal amount;

    private BigDecimal amountExTax;

    private BigDecimal taxAmount;

    private LocalDate invoiceDate;

    private Long projectId;

    private String remark;

    private String createBy;

    private String creatorName;

    private LocalDateTime createTime;
}
