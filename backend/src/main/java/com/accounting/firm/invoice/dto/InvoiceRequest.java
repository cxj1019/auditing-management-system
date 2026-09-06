package com.accounting.firm.invoice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 发票登记/编辑请求
 * <p>编辑场景下 contractId 被忽略（所属合同不可变更）；客户由合同所属项目自动带出</p>
 */
@Data
public class InvoiceRequest {

    /** 发票 ID（编辑时必填） */
    private Long id;

    /** 所属合同 ID（登记时必填；编辑时忽略） */
    @NotNull(message = "所属合同不能为空")
    @Positive(message = "合同 ID 不合法")
    private Long contractId;

    /** 发票号码（登记时可后补；开票前必须填写） */
    @Size(max = 50, message = "发票号码长度不能超过 50")
    private String invoiceNo;

    /** 增值税专用发票 / 增值税普通发票 */
    @NotBlank(message = "发票类型不能为空")
    @Size(max = 30, message = "发票类型长度不能超过 30")
    private String type;

    /** 价税合计（元），可空时按 不含税金额 + 税额 自动计算；外币开票时按外币×汇率折算 */
    @Positive(message = "开票金额必须大于 0")
    private BigDecimal amount;

    /** 币种（境外客户可选外币，默认人民币） */
    @Size(max = 10, message = "币种长度不能超过 10")
    private String currency;

    /** 外币金额 */
    @Positive(message = "外币金额必须大于 0")
    private BigDecimal foreignAmount;

    /** 中国银行牌价（每 100 外币兑人民币） */
    @Positive(message = "汇率必须大于 0")
    private BigDecimal exchangeRate;

    /** 牌价发布时间 */
    @Size(max = 40, message = "牌价发布时间长度不能超过 40")
    private String ratePublishTime;

    /** 税率（%，可空） */
    @DecimalMin(value = "0", message = "税率不能为负")
    @DecimalMax(value = "100", message = "税率不能超过 100")
    private BigDecimal taxRate;

    /** 不含税金额（元，人民币模式主输入；外币模式按价税合计自动拆分） */
    @Positive(message = "不含税金额必须大于 0")
    private BigDecimal amountExTax;

    /** 税额（元），可空时按价税合计 − 不含税金额推算 */
    @Positive(message = "税额必须大于 0")
    private BigDecimal taxAmount;

    /** 开票日期（开票动作时必填，登记时可选） */
    private LocalDate invoiceDate;

    /** 发票品名（默认按业务类型字典带出，可改） */
    @Size(max = 100, message = "发票品名长度不能超过 100")
    private String invoiceItem;

    /** 税收编码（默认按业务类型字典带出，可改） */
    @Size(max = 30, message = "税收编码长度不能超过 30")
    private String taxCode;

    /** 税收分类（默认按业务类型字典带出，可改） */
    @Size(max = 100, message = "税收分类长度不能超过 100")
    private String taxClass;

    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

    /** 垫付开票：向客户收取的代垫费用 */
    private Boolean isRecharge;
}
