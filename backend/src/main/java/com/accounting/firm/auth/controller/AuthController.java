package com.accounting.firm.auth.controller;

import com.accounting.firm.auth.dto.ChangePasswordRequest;
import com.accounting.firm.auth.dto.LoginRequest;
import com.accounting.firm.auth.dto.LoginResponse;
import com.accounting.firm.auth.service.AuthService;
import com.accounting.firm.common.aop.AuditLog;
import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.common.security.JwtUtils;
import com.accounting.firm.common.security.SecurityUser;
import com.accounting.firm.system.entity.SysMenu;
import com.accounting.firm.system.service.SysMenuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 认证接口：登录、登出、当前用户信息
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;
    private final JwtUtils jwtUtils;
    private final SysMenuService sysMenuService;

    /** 登录 */
    @AuditLog("用户登录")
    @PostMapping("/login")
    public ApiResult<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResult.success(authService.login(request));
    }

    /** 登出：当前令牌立即失效 */
    @AuditLog("用户登出")
    @PostMapping("/logout")
    public ApiResult<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX)) {
            authService.logout(authorization.substring(BEARER_PREFIX.length()));
        }
        return ApiResult.success();
    }

    /** 修改密码：校验原密码，成功后当前令牌立即失效需重新登录 */
    @AuditLog("修改密码")
    @PostMapping("/change-password")
    public ApiResult<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                          @AuthenticationPrincipal SecurityUser user,
                                          @RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.changePassword(user.getUserId(), request.getOldPassword(), request.getNewPassword());
        if (StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX)) {
            authService.logout(authorization.substring(BEARER_PREFIX.length()));
        }
        return ApiResult.success();
    }

    /** 当前登录用户信息（含菜单树与按钮权限），用于页面刷新后恢复会话数据 */
    @GetMapping("/info")
    public ApiResult<LoginResponse> info(@AuthenticationPrincipal SecurityUser user) {
        List<SysMenu> menuTree = sysMenuService.getMenuTreeByUserId(user.getUserId());
        LoginResponse response = LoginResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .menus(menuTree)
                .permissions(List.copyOf(user.getPermissions()))
                .roles(List.copyOf(user.getRoles()))
                .build();
        return ApiResult.success(response);
    }
}
