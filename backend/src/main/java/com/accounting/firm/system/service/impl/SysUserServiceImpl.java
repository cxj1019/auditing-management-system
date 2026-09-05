package com.accounting.firm.system.service.impl;

import com.accounting.firm.common.api.PageResult;
import com.accounting.firm.common.exception.BusinessException;
import com.accounting.firm.system.dto.UserOptionVO;
import com.accounting.firm.system.dto.UserRequest;
import com.accounting.firm.system.dto.UserVO;
import com.accounting.firm.system.entity.SysRole;
import com.accounting.firm.system.entity.SysUser;
import com.accounting.firm.system.entity.SysUserRole;
import com.accounting.firm.system.mapper.SysRoleMapper;
import com.accounting.firm.system.mapper.SysUserMapper;
import com.accounting.firm.system.mapper.SysUserRoleMapper;
import com.accounting.firm.system.service.SysUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 用户服务实现
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final com.accounting.firm.system.mapper.SysDepartmentMapper deptMapper;

    @Override
    public PageResult<UserVO> pageUsers(long current, long size, String keyword) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getNickname, keyword);
        }
        wrapper.orderByDesc(SysUser::getCreateTime);

        Page<SysUser> page = page(new Page<>(current, size), wrapper);
        List<UserVO> records = page.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<UserOptionVO> listOptions() {
        // 排除系统管理员（admin 不参与业务，仅做系统管理）
        List<SysUser> users = lambdaQuery()
                .eq(SysUser::getStatus, 1)
                .ne(SysUser::getUsername, "admin")
                .orderByAsc(SysUser::getNickname)
                .list();
        // 批量补齐部门名称
        List<Long> deptIds = users.stream().map(SysUser::getDeptId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> deptNames = deptIds.isEmpty() ? Map.of()
                : deptMapper.selectBatchIds(deptIds).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                com.accounting.firm.system.entity.SysDepartment::getId,
                                com.accounting.firm.system.entity.SysDepartment::getDeptName));
        return users.stream().map(u -> {
            UserOptionVO vo = new UserOptionVO();
            vo.setId(u.getId());
            vo.setNickname(u.getNickname());
            vo.setUsername(u.getUsername());
            vo.setEmail(u.getEmail());
            vo.setDeptId(u.getDeptId());
            vo.setDeptName(deptNames.get(u.getDeptId()));
            return vo;
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createUser(UserRequest request) {
        // 邮箱唯一性校验（邮箱即登录账号，统一小写存储）
        String email = request.getEmail() == null ? null : request.getEmail().trim().toLowerCase();
        Long emailCount = lambdaQuery().eq(SysUser::getEmail, email).count();
        if (emailCount > 0) {
            throw new BusinessException("邮箱已被其他用户使用");
        }
        if (!StringUtils.hasText(request.getPassword())) {
            throw new BusinessException("密码不能为空");
        }
        SysUser user = new SysUser();
        user.setUsername(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setEmail(email);
        user.setPhone(request.getPhone());
        user.setDeptId(request.getDeptId());
        user.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        save(user);
        saveUserRoles(user.getId(), request.getRoleIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(UserRequest request) {
        if (request.getId() == null) {
            throw new BusinessException("用户 ID 不能为空");
        }
        SysUser user = getById(request.getId());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 邮箱唯一性校验（排除自身，统一小写比较避免大小写绕过）
        String email = request.getEmail() == null ? null : request.getEmail().trim().toLowerCase();
        Long emailCount = lambdaQuery()
                .eq(SysUser::getEmail, email)
                .ne(SysUser::getId, request.getId())
                .count();
        if (emailCount > 0) {
            throw new BusinessException("邮箱已被其他用户使用");
        }
        user.setNickname(request.getNickname());
        user.setEmail(email);
        // 用户名与邮箱保持一致且全小写，避免编辑后大小写变化导致历史单据归属失效
        user.setUsername(email);
        user.setPhone(request.getPhone());
        user.setDeptId(request.getDeptId());
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }
        updateById(user);
        // 重新分配角色
        if (request.getRoleIds() != null) {
            sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                    .eq(SysUserRole::getUserId, user.getId()));
            saveUserRoles(user.getId(), request.getRoleIds());
        }
    }

    /** 保存用户角色关联 */
    private void saveUserRoles(Long userId, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (Long roleId : roleIds) {
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            sysUserRoleMapper.insert(userRole);
        }
    }

    /** 实体转 VO（脱敏，附带角色信息） */
    private UserVO toVO(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setDeptId(user.getDeptId());
        if (user.getDeptId() != null) {
            var dept = deptMapper.selectById(user.getDeptId());
            if (dept != null) vo.setDeptName(dept.getDeptName());
        }
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        List<Long> roleIds = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, user.getId()))
                .stream().map(SysUserRole::getRoleId).toList();
        vo.setRoleIds(roleIds);
        if (!roleIds.isEmpty()) {
            vo.setRoleNames(sysRoleMapper.selectBatchIds(roleIds).stream()
                    .map(SysRole::getRoleName).toList());
        }
        return vo;
    }

    @SuppressWarnings("unused")
    private boolean sameUser(Long a, Long b) {
        return Objects.equals(a, b);
    }
}
