package com.accounting.firm.system.service;

import com.accounting.firm.common.api.PageResult;
import com.accounting.firm.system.dto.UserOptionVO;
import com.accounting.firm.system.dto.UserRequest;
import com.accounting.firm.system.dto.UserVO;
import com.accounting.firm.system.entity.SysUser;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 用户服务
 */
public interface SysUserService extends IService<SysUser> {

    /** 分页查询用户 */
    PageResult<UserVO> pageUsers(long current, long size, String keyword);

    /** 在册人员选项（启用状态用户） */
    List<UserOptionVO> listOptions();

    /** 创建用户（密码 BCrypt 加密） */
    void createUser(UserRequest request);

    /** 编辑用户（密码为空表示不修改） */
    void updateUser(UserRequest request);
}
