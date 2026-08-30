package com.accounting.firm.confirmation.service.impl;

import com.accounting.firm.common.api.PageResult;
import com.accounting.firm.common.exception.BusinessException;
import com.accounting.firm.common.security.DataScopeService;
import com.accounting.firm.confirmation.dto.ConfirmationRequest;
import com.accounting.firm.confirmation.entity.Confirmation;
import com.accounting.firm.confirmation.entity.ConfirmationStatus;
import com.accounting.firm.confirmation.mapper.ConfirmationMapper;
import com.accounting.firm.confirmation.service.ConfirmationService;
import com.accounting.firm.project.entity.Project;
import com.accounting.firm.project.entity.ProjectStatus;
import com.accounting.firm.project.mapper.ProjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 函证服务实现（编号人工填写）
 */
@Service
public class ConfirmationServiceImpl extends ServiceImpl<ConfirmationMapper, Confirmation>
        implements ConfirmationService {

    @Value("${confirmation.overdue-days:30}")
    private int overdueDays;

    private final ProjectMapper projectMapper;
    private final DataScopeService dataScopeService;

    public ConfirmationServiceImpl(ProjectMapper projectMapper, DataScopeService dataScopeService) {
        this.projectMapper = projectMapper;
        this.dataScopeService = dataScopeService;
    }

    @Override
    public PageResult<Confirmation> pageConfirmations(long current, long size,
                                                       Integer status, String type, String keyword,
                                                       Long projectId) {
        LambdaQueryWrapper<Confirmation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null, Confirmation::getStatus, status)
                .eq(StringUtils.hasText(type), Confirmation::getType, type)
                .eq(projectId != null, Confirmation::getProjectId, projectId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Confirmation::getConfirmationNo, keyword)
                    .or().like(Confirmation::getTargetUnit, keyword)
                    .or().like(Confirmation::getSummary, keyword));
        }
        wrapper.orderByDesc(Confirmation::getCreateTime);
        // 合伙人/员工只能看本部门（或自己）的数据
        List<String> scope = dataScopeService.getDeptScopedUsernames();
        if (scope != null) {
            wrapper.in(Confirmation::getCreateBy, scope);
        }
        Page<Confirmation> page = page(new Page<>(current, size), wrapper);
        LocalDate deadline = LocalDate.now().minusDays(overdueDays);
        page.getRecords().forEach(c -> {
            if (c.getStatus() == ConfirmationStatus.SENT.getCode()
                    && c.getSentDate() != null && c.getSentDate().isBefore(deadline)) {
                c.setOverdue(true);
            }
        });
        // 批量填充项目名称
        List<Long> projectIds = page.getRecords().stream()
                .map(Confirmation::getProjectId).filter(java.util.Objects::nonNull).distinct().toList();
        if (!projectIds.isEmpty()) {
            Map<Long, Project> projectMap = projectMapper.selectBatchIds(projectIds).stream()
                    .collect(java.util.stream.Collectors.toMap(Project::getId, java.util.function.Function.identity()));
            page.getRecords().forEach(c -> {
                Project p = projectMap.get(c.getProjectId());
                if (p != null) c.setProjectName(p.getName());
            });
        }
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public void createConfirmation(ConfirmationRequest request) {
        requireValidProject(request.getProjectId());
        // 编号唯一性校验
        Long count = lambdaQuery().eq(Confirmation::getConfirmationNo, request.getConfirmationNo()).count();
        if (count > 0) {
            throw new BusinessException("函证编号已存在");
        }
        Confirmation confirmation = new Confirmation();
        confirmation.setConfirmationNo(request.getConfirmationNo());
        copyFields(request, confirmation);
        confirmation.setStatus(ConfirmationStatus.NOT_SENT.getCode());
        confirmation.setHasReply(false);
        save(confirmation);
    }

    @Override
    public void updateConfirmation(ConfirmationRequest request) {
        if (request.getId() == null) {
            throw new BusinessException("函证 ID 不能为空");
        }
        Confirmation confirmation = getById(request.getId());
        if (confirmation == null) {
            throw new BusinessException("函证不存在");
        }
        requireValidProject(request.getProjectId());
        // 编号唯一性校验（排除自身）
        Long count = lambdaQuery()
                .eq(Confirmation::getConfirmationNo, request.getConfirmationNo())
                .ne(Confirmation::getId, request.getId())
                .count();
        if (count > 0) {
            throw new BusinessException("函证编号已存在");
        }
        // 编号不可修改（编辑时保持原编号）
        copyFields(request, confirmation);
        // 回函不相符须填不符原因
        if (Boolean.FALSE.equals(request.getReplyMatched())
                && !StringUtils.hasText(request.getDiscrepancyReason())) {
            throw new BusinessException("回函不相符时须填写不符原因");
        }
        updateById(confirmation);
    }

    @Override
    public void deleteConfirmation(Long id) {
        Confirmation confirmation = getById(id);
        if (confirmation == null) {
            throw new BusinessException("函证不存在");
        }
        if (confirmation.getStatus() != ConfirmationStatus.NOT_SENT.getCode()) {
            throw new BusinessException("仅未发出的函证可删除");
        }
        removeById(id);
    }

    @Override
    public void changeStatus(Long id, String action, LocalDate date) {
        Confirmation confirmation = getById(id);
        if (confirmation == null) {
            throw new BusinessException("函证不存在");
        }
        ConfirmationStatus current = ConfirmationStatus.of(confirmation.getStatus());
        switch (action == null ? "" : action) {
            case "send" -> {
                if (date == null) {
                    throw new BusinessException("发出日期不能为空");
                }
                current.transitionTo(ConfirmationStatus.SENT);
                confirmation.setStatus(ConfirmationStatus.SENT.getCode());
                confirmation.setSentDate(date);
            }
            case "confirm" -> {
                if (date == null) {
                    throw new BusinessException("回函日期不能为空");
                }
                current.transitionTo(ConfirmationStatus.CONFIRMED);
                confirmation.setStatus(ConfirmationStatus.CONFIRMED.getCode());
                confirmation.setConfirmedDate(date);
                confirmation.setHasReply(true);
                // 不相符须填原因
                if (Boolean.FALSE.equals(confirmation.getReplyMatched())
                        && !StringUtils.hasText(confirmation.getDiscrepancyReason())) {
                    throw new BusinessException("回函不相符时须填写不符原因");
                }
            }
            case "void" -> {
                current.transitionTo(ConfirmationStatus.VOIDED);
                confirmation.setStatus(ConfirmationStatus.VOIDED.getCode());
            }
            default -> throw new BusinessException("非法的流转动作");
        }
        updateById(confirmation);
    }

    /** 校验关联项目必填且存在、未归档 */
    private void requireValidProject(Long projectId) {
        if (projectId == null) {
            throw new BusinessException("函证必须关联项目");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("关联项目不存在");
        }
        if (project.getStatus() == ProjectStatus.ARCHIVED.getCode()) {
            throw new BusinessException("项目已归档，不可关联函证");
        }
    }

    /** 复制可编辑字段（不含编号/状态/日期/hasReply） */
    private void copyFields(ConfirmationRequest request, Confirmation confirmation) {
        confirmation.setType(request.getType());
        confirmation.setConfirmationMethod(request.getConfirmationMethod());
        confirmation.setTargetUnit(request.getTargetUnit());
        confirmation.setSummary(request.getSummary());
        confirmation.setProjectId(request.getProjectId());
        confirmation.setSendTrackingNo(request.getSendTrackingNo());
        confirmation.setReplyTrackingNo(request.getReplyTrackingNo());
        confirmation.setReplyMatched(request.getReplyMatched());
        confirmation.setDiscrepancyReason(request.getDiscrepancyReason());
    }
}
