package com.accounting.firm.system.service;

import com.accounting.firm.system.entity.SysMenu;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 菜单服务
 */
public interface SysMenuService extends IService<SysMenu> {

    /** 查询用户可见的菜单树（目录/菜单，不含按钮） */
    List<SysMenu> getMenuTreeByUserId(Long userId);

    /** 查询全部菜单树（管理端，含按钮） */
    List<SysMenu> getMenuTree();

    /** 新增菜单 */
    void createMenu(SysMenu menu);

    /** 更新菜单 */
    void updateMenu(SysMenu menu);
}
