package com.accounting.firm.system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 用户创建/编辑请求（邮箱即登录账号，无需单独的用户名）
 */
@Data
public class UserRequest {

    /** 用户 ID（编辑时必填） */
    private Long id;

    /** 邮箱（必填且全局唯一，作为登录账号） */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过 100")
    private String email;

    /** 密码：创建时必填；编辑时为空表示不修改 */
    @Size(max = 100, message = "密码长度不能超过 100")
    private String password;

    @Size(max = 50, message = "姓名长度不能超过 50")
    private String nickname;

    /** 部门 ID（必填） */
    @NotNull(message = "部门不能为空")
    private Long deptId;

    @Size(max = 20, message = "手机号长度不能超过 20")
    private String phone;

    /** 状态：1-启用 0-禁用 */
    private Integer status;

    /** 关联角色 ID 集合 */
    private List<Long> roleIds;
}
