package com.accounting.firm.system.mapper;

import com.accounting.firm.system.entity.SysMenu;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 菜单 Mapper
 */
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /** 查询用户拥有的全部菜单（含按钮权限点） */
    @Select("SELECT DISTINCT m.* FROM sys_menu m " +
            "JOIN sys_role_menu rm ON rm.menu_id = m.id " +
            "JOIN sys_user_role ur ON ur.role_id = rm.role_id " +
            "WHERE ur.user_id = #{userId} " +
            "ORDER BY m.sort ASC")
    List<SysMenu> selectMenusByUserId(@Param("userId") Long userId);

    /** 查询用户的按钮级权限标识集合 */
    @Select("SELECT DISTINCT m.perm FROM sys_menu m " +
            "JOIN sys_role_menu rm ON rm.menu_id = m.id " +
            "JOIN sys_user_role ur ON ur.role_id = rm.role_id " +
            "WHERE ur.user_id = #{userId} AND m.perm IS NOT NULL AND m.perm != ''")
    List<String> selectPermsByUserId(@Param("userId") Long userId);
}
