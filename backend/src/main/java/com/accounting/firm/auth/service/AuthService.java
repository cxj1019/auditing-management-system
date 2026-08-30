package com.accounting.firm.auth.service;

import com.accounting.firm.auth.dto.LoginRequest;
import com.accounting.firm.auth.dto.LoginResponse;
import com.accounting.firm.common.api.ResultCode;
import com.accounting.firm.common.exception.BusinessException;
import com.accounting.firm.common.security.JwtUtils;
import com.accounting.firm.common.security.SecurityUser;
import com.accounting.firm.common.security.TokenBlacklistService;
import com.accounting.firm.system.entity.SysMenu;
import com.accounting.firm.system.entity.SysUser;
import com.accounting.firm.system.mapper.SysUserMapper;
import com.accounting.firm.system.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 认证服务：登录、登出、获取当前用户信息
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final TokenBlacklistService tokenBlacklistService;
    private final SysMenuService sysMenuService;
    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 登录：校验账号密码，签发 JWT，返回用户信息与菜单权限
     */
    public LoginResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        } catch (Exception e) {
            // 统一提示，不区分账号是否存在
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        SecurityUser user = (SecurityUser) authentication.getPrincipal();
        String token = jwtUtils.generateToken(user.getUserId(), user.getUsername());

        List<SysMenu> menuTree = sysMenuService.getMenuTreeByUserId(user.getUserId());
        List<String> permissions = List.copyOf(user.getPermissions());

        return LoginResponse.builder()
                .token(token)
                .userId(user.getUserId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .menus(menuTree)
                .permissions(permissions)
                .roles(List.copyOf(user.getRoles()))
                .build();
    }

    /**
     * 修改密码：校验原密码后更新为 BCrypt 新密码
     */
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("原密码不正确");
        }
        SysUser patch = new SysUser();
        patch.setId(userId);
        patch.setPassword(passwordEncoder.encode(newPassword));
        sysUserMapper.updateById(patch);
        log.info("用户 {} 已修改密码", user.getUsername());
    }

    /**
     * 登出：将当前令牌 jti 加入黑名单，使其立即失效
     */
    public void logout(String token) {
        try {
            String jti = jwtUtils.getJtiFromToken(token);
            tokenBlacklistService.blacklist(jti, jwtUtils.getExpirationFromToken(token));
            log.info("令牌已加入黑名单: jti={}", jti);
        } catch (Exception e) {
            // 令牌已无效时登出视为成功
            log.debug("登出时令牌解析失败（视为已失效）: {}", e.getMessage());
        }
    }
}
