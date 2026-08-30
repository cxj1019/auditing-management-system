package com.accounting.firm.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 角色创建/编辑请求
 */
@Data
public class RoleRequest {

    /** 角色 ID（编辑时必填） */
    private Long id;

    @NotBlank(message = "角色编码不能为空")
    @Size(max = 50, message = "角色编码长度不能超过 50")
    private String roleCode;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 50, message = "角色名称长度不能超过 50")
    private String roleName;

    @Size(max = 200, message = "角色描述长度不能超过 200")
    private String description;

    /** 状态：1-启用 0-禁用 */
    private Integer status;

    /** 分配的菜单/按钮权限 ID 集合 */
    private List<Long> menuIds;
}
