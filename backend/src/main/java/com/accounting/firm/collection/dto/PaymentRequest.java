package com.accounting.firm.collection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 收款登记/编辑请求
 * <p>两种模式：核销收款（invoiceId 必填，须为已开票发票，合同自动带出）；
 * 预收收款（仅传 contractId，暂不挂发票，开票后可通过核销操作关联发票）。
 * 编辑场景下 invoiceId/contractId 均被忽略（归属不可变更）</p>
 */
@Data
public class PaymentRequest {

    /** 所属发票 ID（核销收款时必填，须为已开票发票；编辑时忽略） */
    @Positive(message = "发票 ID 不合法")
    private Long invoiceId;

    /** 所属合同 ID（预收收款时必填；编辑时忽略） */
    @Positive(message = "合同 ID 不合法")
    private Long contractId;

    @NotNull(message = "收款金额不能为空")
    @Positive(message = "收款金额必须大于 0")
    private BigDecimal amount;

    @NotNull(message = "收款日期不能为空")
    private LocalDate paymentDate;

    @NotBlank(message = "收款方式不能为空")
    @Size(max = 30, message = "收款方式长度不能超过 30")
    private String paymentMethod;

    @Size(max = 100, message = "付款方长度不能超过 100")
    private String payerName;

    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;
}
