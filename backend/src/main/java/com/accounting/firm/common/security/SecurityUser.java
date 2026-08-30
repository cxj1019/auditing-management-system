package com.accounting.firm.common.security;

import com.accounting.firm.system.entity.SysUser;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * Spring Security 用户详情
 * <p>携带用户基本信息、权限标识集合与角色编码集合</p>
 */
@Getter
public class SecurityUser implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String username;
    private final String password;
    private final String nickname;
    private final Integer status;
    private final Long deptId;
    /** 按钮级权限标识集合，如 system:user:add */
    private final Set<String> permissions;
    /** 角色编码集合，如 admin / manager / employee */
    private final Set<String> roles;

    public SecurityUser(SysUser user, Set<String> permissions, Set<String> roles) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.nickname = user.getNickname();
        this.status = user.getStatus();
        this.deptId = user.getDeptId();
        this.permissions = permissions == null ? Collections.emptySet() : permissions;
        this.roles = roles == null ? Collections.emptySet() : roles;
    }

    /** 权限标识作为 GrantedAuthority 返回 */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    /** 判断当前用户是否具备指定权限标识 */
    public boolean hasPermission(String perm) {
        return permissions.contains(perm);
    }

    /** 判断当前用户是否具备指定角色编码 */
    public boolean hasRole(String roleCode) {
        return roles.contains(roleCode);
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != null && status == 1;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status != null && status == 1;
    }
}
