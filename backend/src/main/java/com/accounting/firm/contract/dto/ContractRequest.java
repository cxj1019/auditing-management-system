package com.accounting.firm.contract.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 合同创建/编辑请求
 * <p>合同编号由系统生成、状态由流转接口维护，均不通过本请求修改</p>
 */
@Data
public class ContractRequest {

    /** 合同 ID（编辑时必填） */
    private Long id;

    /** 所属项目 ID（登记时必填；编辑不可变更） */
    @NotNull(message = "所属项目不能为空")
    @Positive(message = "项目 ID 不合法")
    private Long projectId;

    /** 合同名称（可不填，留空时以合同字号代替） */
    @Size(max = 200, message = "合同名称长度不能超过 200")
    private String name;

    @NotBlank(message = "合同类型不能为空")
    @Size(max = 50, message = "合同类型长度不能超过 50")
    private String contractType;

    /** 业务类型（来自业务类型字典，决定字号与开票要素） */
    @Size(max = 100, message = "业务类型长度不能超过 100")
    private String bizType;

    @NotNull(message = "合同金额不能为空")
    private BigDecimal amount;

    /** 税率（%，可空） */
    private BigDecimal taxRate;

    /** 不含税金额（元，可空；未填时按含税金额与税率反拆） */
    @Positive(message = "不含税金额必须大于 0")
    private BigDecimal amountExTax;

    /** 税额（元，可空） */
    @PositiveOrZero(message = "税额不能为负")
    private BigDecimal taxAmount;

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

    /** 签约日期（可空；编号年份未填时按当前年份） */
    private LocalDate signDate;

    /** 服务期限开始日期（可空：未约定期间时留空） */
    private LocalDate serviceStart;

    /** 服务期限结束日期（可空） */
    private LocalDate serviceEnd;

    @NotBlank(message = "合同保管人不能为空")
    @Size(max = 50, message = "合同保管人长度不能超过 50")
    private String keeperName;

    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;
}
