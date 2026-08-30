package com.accounting.firm.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜单实体（目录/菜单/按钮权限点一体建模）
 */
@Data
@TableName("sys_menu")
public class SysMenu implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 菜单类型：目录 */
    public static final int TYPE_DIR = 0;
    /** 菜单类型：菜单 */
    public static final int TYPE_MENU = 1;
    /** 菜单类型：按钮 */
    public static final int TYPE_BUTTON = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父菜单 ID，0 表示根节点 */
    private Long parentId;

    /** 菜单/按钮名称 */
    private String name;

    /** 路由路径（目录/菜单） */
    private String path;

    /** 前端组件路径（菜单），如 system/user/index */
    private String component;

    /** 权限标识，如 system:user:add */
    private String perm;

    /** 图标 */
    private String icon;

    /** 类型：0-目录 1-菜单 2-按钮 */
    private Integer type;

    /** 排序号，越小越靠前 */
    private Integer sort;

    /** 是否可见：1-是 0-隐藏 */
    private Integer visible;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 子菜单（非数据库字段，用于树形结构） */
    @TableField(exist = false)
    private List<SysMenu> children = new ArrayList<>();
}
