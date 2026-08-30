package com.accounting.firm.system.service.impl;

import com.accounting.firm.common.exception.BusinessException;
import com.accounting.firm.system.entity.SysMenu;
import com.accounting.firm.system.mapper.SysMenuMapper;
import com.accounting.firm.system.service.SysMenuService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 菜单服务实现
 */
@Service
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {

    @Override
    public List<SysMenu> getMenuTreeByUserId(Long userId) {
        List<SysMenu> menus = baseMapper.selectMenusByUserId(userId);
        // 只保留目录与菜单，按钮不进入导航树
        List<SysMenu> navMenus = menus.stream()
                .filter(m -> m.getType() == SysMenu.TYPE_DIR || m.getType() == SysMenu.TYPE_MENU)
                .toList();
        return buildTree(navMenus, 0L);
    }

    @Override
    public List<SysMenu> getMenuTree() {
        List<SysMenu> menus = list(new LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getSort));
        return buildTree(menus, 0L);
    }

    @Override
    public void createMenu(SysMenu menu) {
        validateParent(menu);
        save(menu);
    }

    @Override
    public void updateMenu(SysMenu menu) {
        if (menu.getId() == null) {
            throw new BusinessException("菜单 ID 不能为空");
        }
        if (Objects.equals(menu.getId(), menu.getParentId())) {
            throw new BusinessException("父菜单不能是自身");
        }
        validateParent(menu);
        updateById(menu);
    }

    /** 校验父节点存在 */
    private void validateParent(SysMenu menu) {
        if (menu.getParentId() != null && menu.getParentId() != 0) {
            SysMenu parent = getById(menu.getParentId());
            if (parent == null) {
                throw new BusinessException("父菜单不存在");
            }
        }
    }

    /** 递归构建菜单树 */
    private List<SysMenu> buildTree(List<SysMenu> menus, Long parentId) {
        return menus.stream()
                .filter(m -> Objects.equals(m.getParentId(), parentId))
                .sorted(Comparator.comparing(SysMenu::getSort, Comparator.nullsLast(Comparator.naturalOrder())))
                .peek(m -> m.setChildren(buildTree(menus, m.getId())))
                .toList();
    }
}
