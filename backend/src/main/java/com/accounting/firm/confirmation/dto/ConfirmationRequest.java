package com.accounting.firm.confirmation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 函证登记/编辑请求（编号人工填写）
 */
@Data
public class ConfirmationRequest {

    /** 函证 ID（编辑时必填） */
    private Long id;

    /** 函证编号（人工填写，全局唯一） */
    @NotBlank(message = "函证编号不能为空")
    @Size(max = 30, message = "函证编号长度不能超过 30")
    private String confirmationNo;

    @NotBlank(message = "函证类型不能为空")
    @Size(max = 30, message = "函证类型长度不能超过 30")
    private String type;

    /** 函证方式：邮寄/电子/现场/其他 */
    @Size(max = 30, message = "函证方式长度不能超过 30")
    private String confirmationMethod;

    @NotBlank(message = "被函证单位不能为空")
    @Size(max = 200, message = "被函证单位长度不能超过 200")
    private String targetUnit;

    @NotBlank(message = "函证内容摘要不能为空")
    @Size(max = 500, message = "函证内容摘要长度不能超过 500")
    private String summary;

    /** 关联项目 ID（必填：函证必须挂在项目下） */
    @NotNull(message = "关联项目不能为空")
    @Positive(message = "项目 ID 不合法")
    private Long projectId;

    /** 发出快递单号 */
    @Size(max = 100, message = "发出快递单号长度不能超过 100")
    private String sendTrackingNo;

    /** 回函快递单号 */
    @Size(max = 100, message = "回函快递单号长度不能超过 100")
    private String replyTrackingNo;

    /** 回函是否相符 */
    private Boolean replyMatched;

    /** 不符原因 */
    @Size(max = 500, message = "不符原因长度不能超过 500")
    private String discrepancyReason;
}
