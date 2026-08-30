package com.accounting.firm.client.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 客户实体
 */
@Data
@TableName("client")
public class Client implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客户编号：KH + yyyyMMdd + 4 位流水 */
    private String clientNo;

    /** 客户名称 */
    private String clientName;

    /** 客户类型：domestic=境内 overseas=境外 */
    private String clientType;

    /** 所属部门 ID */
    private Long deptId;

    /** 统一社会信用代码 */
    private String creditCode;

    /** 注册资本 */
    private String registeredCapital;

    /** 注册地 */
    private String registeredAddress;

    /** 法定代表人 */
    private String legalRepresentative;

    /** 经营范围 */
    private String businessScope;

    /** 联系人 */
    private String contactPerson;

    /** 联系电话 */
    private String contactPhone;

    /** 开票抬头 */
    private String invoiceTitle;

    /** 纳税人识别号 */
    private String invoiceTaxNo;

    /** 开户银行 */
    private String invoiceBankName;

    /** 银行账号 */
    private String invoiceBankAccount;

    /** 开票地址 */
    private String invoiceAddress;

    /** 开票电话 */
    private String invoicePhone;

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
