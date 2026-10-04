package com.accounting.firm.vendor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 供应商主数据 */
@Data
@TableName("vendor")
public class Vendor implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String vendorName;

    private String taxNo;

    private String bankAccount;

    private String contact;

    private String phone;

    private String remark;

    private String createBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
