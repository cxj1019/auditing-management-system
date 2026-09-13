package com.accounting.firm.vendor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 对公付款附件（发票扫描件/凭证），存 Supabase 私有桶 */
@Data
@TableName("vendor_payment_attachment")
public class VendorPaymentAttachment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long paymentId;

    private String fileName;

    private String storedName;

    private Long fileSize;

    private String contentType;

    private String createBy;

    private LocalDateTime createTime;
}
