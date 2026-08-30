package com.accounting.firm.common.security;

import com.accounting.firm.common.api.ResultCode;
import com.accounting.firm.common.exception.BusinessException;
import com.accounting.firm.system.mapper.SysMenuMapper;
import com.accounting.firm.system.mapper.SysRoleMapper;
import com.accounting.firm.system.mapper.SysUserMapper;
import com.accounting.firm.system.entity.SysUser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * 用户详情加载服务：根据账号或邮箱加载用户、权限与角色
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper sysUserMapper;
    private final SysMenuMapper sysMenuMapper;
    private final SysRoleMapper sysRoleMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 登录凭证同时匹配账号或绑定邮箱（邮箱大小写不敏感）
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .or()
                .apply("LOWER(email) = LOWER({0})", username)
                .last("LIMIT 1"));
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        Set<String> permissions = new HashSet<>(sysMenuMapper.selectPermsByUserId(user.getId()));
        Set<String> roles = new HashSet<>(sysRoleMapper.selectRoleCodesByUserId(user.getId()));
        return new SecurityUser(user, permissions, roles);
    }
}
