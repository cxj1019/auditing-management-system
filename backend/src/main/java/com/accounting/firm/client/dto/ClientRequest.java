package com.accounting.firm.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 客户创建/编辑请求
 */
@Data
public class ClientRequest {

    private Long id;

    @NotBlank(message = "客户名称不能为空")
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String clientName;

    /** domestic=境内 overseas=境外 */
    @NotBlank(message = "客户类型不能为空")
    private String clientType;

    @Size(max = 50, message = "统一信用代码长度不能超过 50")
    private String creditCode;

    @Size(max = 100, message = "注册资本长度不能超过 100")
    private String registeredCapital;

    @Size(max = 500, message = "注册地长度不能超过 500")
    private String registeredAddress;

    @Size(max = 100, message = "法定代表人长度不能超过 100")
    private String legalRepresentative;

    private String businessScope;

    @Size(max = 100, message = "联系人长度不能超过 100")
    private String contactPerson;

    @Size(max = 50, message = "联系电话长度不能超过 50")
    private String contactPhone;

    /** 开票信息（可选，用于开具增值税发票） */
    @Size(max = 200, message = "开票抬头长度不能超过 200")
    private String invoiceTitle;

    @Size(max = 50, message = "纳税人识别号长度不能超过 50")
    private String invoiceTaxNo;

    @Size(max = 200, message = "开户银行长度不能超过 200")
    private String invoiceBankName;

    @Size(max = 100, message = "银行账号长度不能超过 100")
    private String invoiceBankAccount;

    @Size(max = 500, message = "开票地址长度不能超过 500")
    private String invoiceAddress;

    @Size(max = 50, message = "开票电话长度不能超过 50")
    private String invoicePhone;

    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;
}
