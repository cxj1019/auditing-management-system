package com.accounting.firm.system.service.impl;

import com.accounting.firm.common.exception.BusinessException;
import com.accounting.firm.system.dto.RoleRequest;
import com.accounting.firm.system.entity.SysRole;
import com.accounting.firm.system.entity.SysRoleMenu;
import com.accounting.firm.system.mapper.SysRoleMapper;
import com.accounting.firm.system.mapper.SysRoleMenuMapper;
import com.accounting.firm.system.service.SysRoleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色服务实现
 */
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    private final SysRoleMenuMapper sysRoleMenuMapper;

    @Override
    public List<SysRole> listRoles() {
        return lambdaQuery().orderByAsc(SysRole::getId).list();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createRole(RoleRequest request) {
        Long count = lambdaQuery().eq(SysRole::getRoleCode, request.getRoleCode()).count();
        if (count > 0) {
            throw new BusinessException("角色编码已存在");
        }
        SysRole role = new SysRole();
        role.setRoleCode(request.getRoleCode());
        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());
        role.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        save(role);
        if (request.getMenuIds() != null) {
            assignMenus(role.getId(), request.getMenuIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(RoleRequest request) {
        if (request.getId() == null) {
            throw new BusinessException("角色 ID 不能为空");
        }
        SysRole role = getById(request.getId());
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        Long count = lambdaQuery()
                .eq(SysRole::getRoleCode, request.getRoleCode())
                .ne(SysRole::getId, request.getId())
                .count();
        if (count > 0) {
            throw new BusinessException("角色编码已存在");
        }
        role.setRoleCode(request.getRoleCode());
        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());
        if (request.getStatus() != null) {
            role.setStatus(request.getStatus());
        }
        updateById(role);
        if (request.getMenuIds() != null) {
            assignMenus(role.getId(), request.getMenuIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long roleId, List<Long> menuIds) {
        sysRoleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getRoleId, roleId));
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        for (Long menuId : menuIds) {
            SysRoleMenu roleMenu = new SysRoleMenu();
            roleMenu.setRoleId(roleId);
            roleMenu.setMenuId(menuId);
            sysRoleMenuMapper.insert(roleMenu);
        }
    }

    @Override
    public List<Long> getMenuIdsByRoleId(Long roleId) {
        return sysRoleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                        .eq(SysRoleMenu::getRoleId, roleId))
                .stream().map(SysRoleMenu::getMenuId).toList();
    }
}
