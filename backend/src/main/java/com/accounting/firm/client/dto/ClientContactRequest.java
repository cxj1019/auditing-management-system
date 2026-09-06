package com.accounting.firm.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 客户联系人创建/编辑请求
 */
@Data
public class ClientContactRequest {

    /** 联系人 ID（编辑时必填） */
    private Long id;

    /** 所属客户 ID（路径参数携带，可空） */
    private Long clientId;

    @NotBlank(message = "联系人姓名不能为空")
    @Size(max = 100, message = "联系人姓名长度不能超过 100")
    private String contactName;

    @Size(max = 100, message = "职务长度不能超过 100")
    private String position;

    @Size(max = 50, message = "联系电话长度不能超过 50")
    private String phone;

    @Size(max = 100, message = "电子邮箱长度不能超过 100")
    private String email;

    @Size(max = 200, message = "备注长度不能超过 200")
    private String remark;
}
