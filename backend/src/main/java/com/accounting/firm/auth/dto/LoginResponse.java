package com.accounting.firm.auth.dto;

import com.accounting.firm.system.entity.SysMenu;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 登录响应：令牌 + 用户信息 + 菜单树 + 按钮权限标识
 */
@Data
@Builder
public class LoginResponse {

    /** 访问令牌 */
    private String token;

    /** 用户 ID */
    private Long userId;

    /** 登录账号 */
    private String username;

    /** 姓名/昵称 */
    private String nickname;

    /** 菜单树（目录/菜单，不含按钮） */
    private List<SysMenu> menus;

    /** 按钮级权限标识集合 */
    private List<String> permissions;

    /** 角色编码（admin/manager/employee），用于前端区分展示 */
    private List<String> roles;
}
