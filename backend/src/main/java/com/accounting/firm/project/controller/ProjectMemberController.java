package com.accounting.firm.project.controller;

import com.accounting.firm.common.api.ApiResult;
import com.accounting.firm.project.entity.ProjectMember;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accounting.firm.project.mapper.ProjectMemberMapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目参与人员接口
 */
@RestController
@RequestMapping("/api/projects/{projectId}/members")
@RequiredArgsConstructor
public class ProjectMemberController {

    private final ProjectMemberMapper memberMapper;

    /** 参与人员清单 */
    @PreAuthorize("hasAuthority('business:project:list')")
    @GetMapping
    public ApiResult<List<ProjectMember>> list(@PathVariable Long projectId) {
        return ApiResult.success(memberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .orderByAsc(ProjectMember::getSort)));
    }

    /** 添加参与人员 */
    @PreAuthorize("hasAuthority('business:project:edit')")
    @PostMapping
    public ApiResult<Void> add(@PathVariable Long projectId,
                              @RequestParam String memberName,
                              @RequestParam String memberRole) {
        ProjectMember member = new ProjectMember();
        member.setProjectId(projectId);
        member.setMemberName(memberName);
        member.setMemberRole(memberRole);
        member.setSort(0);
        member.setCreateTime(LocalDateTime.now());
        memberMapper.insert(member);
        return ApiResult.success();
    }

    /** 移除参与人员 */
    @PreAuthorize("hasAuthority('business:project:edit')")
    @DeleteMapping("/{memberId}")
    public ApiResult<Void> remove(@PathVariable Long projectId, @PathVariable Long memberId) {
        memberMapper.deleteById(memberId);
        return ApiResult.success();
    }
}
