package com.accounting.firm.project.service.impl;

import com.accounting.firm.client.entity.Client;
import com.accounting.firm.client.mapper.ClientMapper;
import com.accounting.firm.common.api.PageResult;
import com.accounting.firm.common.exception.BusinessException;
import com.accounting.firm.common.security.DataScopeService;
import com.accounting.firm.contract.entity.Contract;
import com.accounting.firm.contract.mapper.ContractMapper;
import com.accounting.firm.project.dto.ProjectRequest;
import com.accounting.firm.project.entity.Project;
import com.accounting.firm.project.entity.ProjectStatus;
import com.accounting.firm.project.mapper.ProjectMapper;
import com.accounting.firm.project.service.ProjectNoGenerator;
import com.accounting.firm.project.service.ProjectService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 项目服务实现
 */
@Service
public class ProjectServiceImpl extends ServiceImpl<ProjectMapper, Project> implements ProjectService {

    private final ContractMapper contractMapper;
    private final DataScopeService dataScopeService;
    private final ClientMapper clientMapper;

    public ProjectServiceImpl(@Lazy ContractMapper contractMapper, DataScopeService dataScopeService, ClientMapper clientMapper) {
        this.contractMapper = contractMapper;
        this.dataScopeService = dataScopeService;
        this.clientMapper = clientMapper;
    }

    @Override
    public PageResult<Project> pageProjects(long current, long size,
                                            Integer status, String type, String keyword,
                                            LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null, Project::getStatus, status)
                .eq(StringUtils.hasText(type), Project::getType, type);
        if (StringUtils.hasText(keyword)) {
            // clientName 为联表字段，按客户名搜索走 EXISTS 子查询关联客户表
            wrapper.and(w -> w.like(Project::getProjectNo, keyword)
                    .or().like(Project::getName, keyword)
                    .or().apply("EXISTS (SELECT 1 FROM client c WHERE c.id = project.client_id"
                            + " AND c.client_name LIKE {0})", "%" + keyword + "%"));
        }
        // 合伙人只能看本部门数据
        List<String> scope = dataScopeService.getDeptScopedUsernames();
        if (scope != null) {
            wrapper.in(Project::getCreateBy, scope);
        }
        wrapper.ge(startDate != null, Project::getStartDate, startDate)
                .le(endDate != null, Project::getStartDate, endDate)
                .orderByDesc(Project::getCreateTime);
        Page<Project> page = page(new Page<>(current, size), wrapper);
        // 批量填充客户名称
        List<Long> clientIds = page.getRecords().stream()
                .map(Project::getClientId).filter(java.util.Objects::nonNull).distinct().toList();
        if (!clientIds.isEmpty()) {
            Map<Long, Client> clientMap = clientMapper.selectBatchIds(clientIds).stream()
                    .collect(java.util.stream.Collectors.toMap(Client::getId, c -> c));
            page.getRecords().forEach(p -> {
                Client c = clientMap.get(p.getClientId());
                if (c != null) p.setClientName(c.getClientName());
            });
        }
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public void createProject(ProjectRequest request) {
        validateDates(request);
        requireValidClient(request.getClientId());
        Project project = new Project();
        copyFields(request, project);
        project.setProjectNo(generateNo());
        project.setStatus(ProjectStatus.IN_PROGRESS.getCode());
        save(project);
    }

    @Override
    public void updateProject(ProjectRequest request) {
        if (request.getId() == null) {
            throw new BusinessException("项目 ID 不能为空");
        }
        Project project = getById(request.getId());
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        if (ProjectStatus.of(project.getStatus()).isFinal()) {
            throw new BusinessException("已归档的项目不可修改");
        }
        validateDates(request);
        requireValidClient(request.getClientId());
        // 仅更新基本信息；编号与状态不可通过编辑修改
        copyFields(request, project);
        updateById(project);
    }

    @Override
    public void deleteProject(Long id) {
        Project project = getById(id);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        if (project.getStatus() != ProjectStatus.IN_PROGRESS.getCode()) {
            throw new BusinessException("仅进行中的项目可删除");
        }
        Long contractCount = contractMapper.selectCount(
                new LambdaQueryWrapper<Contract>().eq(Contract::getProjectId, id));
        if (contractCount > 0) {
            throw new BusinessException("项目下已存在合同，请先处理关联合同");
        }
        removeById(id);
    }

    @Override
    public void changeStatus(Long id, String action) {
        Project project = getById(id);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        ProjectStatus current = ProjectStatus.of(project.getStatus());
        ProjectStatus target = switch (action == null ? "" : action) {
            case "finish" -> ProjectStatus.FINISHED;
            case "reopen" -> ProjectStatus.IN_PROGRESS;
            case "archive" -> ProjectStatus.ARCHIVED;
            default -> throw new BusinessException("非法的流转动作");
        };
        current.transitionTo(target);
        project.setStatus(target.getCode());
        updateById(project);
    }

    /** 生成当日项目编号 */
    private String generateNo() {
        LocalDate today = LocalDate.now();
        String prefix = "PRJ" + "%1$tY%1$tm%1$td".formatted(today);
        Project max = lambdaQuery()
                .likeRight(Project::getProjectNo, prefix)
                .orderByDesc(Project::getProjectNo)
                .last("LIMIT 1")
                .one();
        return ProjectNoGenerator.next(today, max == null ? null : max.getProjectNo());
    }

    /** 校验客户存在 */
    private void requireValidClient(Long clientId) {
        if (clientId == null || clientId == 0) {
            throw new BusinessException("客户不能为空");
        }
        Client client = clientMapper.selectById(clientId);
        if (client == null) {
            throw new BusinessException("客户不存在");
        }
    }

    /** 项目期间校验：开始不得晚于结束 */
    private void validateDates(ProjectRequest request) {
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new BusinessException("项目开始日期不能晚于结束日期");
        }
    }

    /** 复制基本信息字段（不含编号/状态） */
    private void copyFields(ProjectRequest request, Project project) {
        project.setName(request.getName());
        project.setType(request.getType());
        project.setBizNature(request.getBizNature());
        project.setBizType(request.getBizType());
        project.setClientId(request.getClientId());
        project.setPartnerName(request.getPartnerName());
        project.setManagerName(request.getManagerName());
        project.setSiteLeaderName(request.getSiteLeaderName());
        project.setStartDate(request.getStartDate());
        project.setEndDate(request.getEndDate());
        project.setRemark(request.getRemark());
    }
}
