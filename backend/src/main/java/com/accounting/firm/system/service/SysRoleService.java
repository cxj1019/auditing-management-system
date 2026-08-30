package com.accounting.firm.system.service;

import com.accounting.firm.system.dto.RoleRequest;
import com.accounting.firm.system.entity.SysRole;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 角色服务
 */
public interface SysRoleService extends IService<SysRole> {

    /** 查询全部角色 */
    List<SysRole> listRoles();

    /** 创建角色 */
    void createRole(RoleRequest request);

    /** 编辑角色 */
    void updateRole(RoleRequest request);

    /** 为角色分配菜单权限（先删后插） */
    void assignMenus(Long roleId, List<Long> menuIds);

    /** 查询角色已分配的菜单 ID 集合 */
    List<Long> getMenuIdsByRoleId(Long roleId);
}
