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
 * <p>数据权限按<b>项目归属部门</b>隔离：项目登记时确定所属部门，
 * 与项目相关的业务数据（合同、发票、函证、收款、报销等）对该部门全体成员可见可编辑；
 * admin 看全部；未分配部门的用户仅能看自己创建的数据。</p>
 */
@Service
@RequiredArgsConstructor
public class DataScopeService {

    private final SysUserMapper sysUserMapper;

    /** 数据范围类型：ALL-全部（admin） DEPT-本部门 SELF-仅本人创建 */
    public enum ScopeType { ALL, DEPT, SELF }

    /**
     * 当前用户的数据范围
     *
     * @param type   范围类型
     * @param deptId DEPT 时的部门 ID
     * @param userId SELF 时的用户 ID
     * @param username SELF 时的用户名（按 create_by 快照过滤用）
     */
    public record Scope(ScopeType type, Long deptId, Long userId, String username) {

        /** 是否不加任何过滤（admin） */
        public boolean isAll() {
            return type == ScopeType.ALL;
        }

        /** 项目部门过滤条件：按 project_id 关联项目部门的子查询条件（须与项目表实际列名一致） */
        public String projectDeptInSql() {
            return "SELECT id FROM project WHERE dept_id = " + deptId;
        }
    }

    /** 解析当前用户的数据范围（未登录等异常情况按 SELF 兜底） */
    public Scope currentScope() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth != null && auth.getPrincipal() instanceof SecurityUser user)) {
            return new Scope(ScopeType.SELF, null, null, "");
        }
        if (user.hasRole("admin")) {
            return new Scope(ScopeType.ALL, null, null, null);
        }
        if (user.getDeptId() != null) {
            return new Scope(ScopeType.DEPT, user.getDeptId(), user.getUserId(), user.getUsername());
        }
        return new Scope(ScopeType.SELF, null, user.getUserId(), user.getUsername());
    }

    /**
     * 判断当前用户是否需要按人员过滤（日程工时等按业务归属人统计的场景）
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
}
