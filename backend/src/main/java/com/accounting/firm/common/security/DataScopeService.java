package com.accounting.firm.common.security;

import com.accounting.firm.system.entity.SysUser;
import com.accounting.firm.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据范围过滤服务
 * <p>按部门隔离业务数据：非 admin 用户只能看到本部门（dept_id 匹配）的数据；
 * admin 和未分配部门的用户不受限制。</p>
 */
@Service
@RequiredArgsConstructor
public class DataScopeService {

    private final SysUserMapper sysUserMapper;

    /**
     * 判断当前用户是否需要部门数据过滤
     * <p>admin 不受限；有部门 → 限定本部门；无部门 → 仅看自己创建的数据。</p>
     *
     * @return 需要过滤时返回允许查看的用户名列表；不需要过滤（admin）返回 null
     */
    public List<String> getDeptScopedUsernames() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth != null && auth.getPrincipal() instanceof SecurityUser user)) {
            return null;
        }
        if (user.hasRole("admin")) {
            return null;
        }
        if (user.getDeptId() != null) {
            return sysUserMapper.selectList(
                            new LambdaQueryWrapper<SysUser>().eq(SysUser::getDeptId, user.getDeptId()))
                    .stream().map(SysUser::getUsername).toList();
        }
        return List.of(user.getUsername());
    }

    /**
     * 获取当前用户数据范围内可见的用户 ID 列表
     * <p>与 {@link #getDeptScopedUsernames()} 同规则，但按用户 ID 表达，
     * 适用于按业务归属人（如日程的 user_id）而非创建人过滤的场景。</p>
     *
     * @return 需要过滤时返回允许查看的用户 ID 列表；不需要过滤（admin）返回 null
     */
    public List<Long> getDeptScopedUserIds() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth != null && auth.getPrincipal() instanceof SecurityUser user)) {
            return null;
        }
        if (user.hasRole("admin")) {
            return null;
        }
        if (user.getDeptId() != null) {
            return sysUserMapper.selectList(
                            new LambdaQueryWrapper<SysUser>().eq(SysUser::getDeptId, user.getDeptId()))
                    .stream().map(SysUser::getId).toList();
        }
        return List.of(user.getUserId());
    }

    /**
     * 获取当前用户的部门 ID
     *
     * @return 部门 ID；admin 或未分配部门的用户返回 null（表示不过滤）
     */
    public Long getCurrentUserDeptId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth != null && auth.getPrincipal() instanceof SecurityUser user)) {
            return null;
        }
        if (user.hasRole("admin")) {
            return null;
        }
        return user.getDeptId();
    }
}
