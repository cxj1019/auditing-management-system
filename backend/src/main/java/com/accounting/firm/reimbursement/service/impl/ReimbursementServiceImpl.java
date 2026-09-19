package com.accounting.firm.reimbursement.service.impl;

import com.accounting.firm.common.api.PageResult;
import com.accounting.firm.common.exception.BusinessException;
import com.accounting.firm.common.security.DataScopeService;
import com.accounting.firm.common.security.SecurityUser;
import com.accounting.firm.system.entity.SysUser;
import com.accounting.firm.system.mapper.SysUserMapper;
import com.accounting.firm.project.entity.Project;
import com.accounting.firm.project.entity.ProjectStatus;
import com.accounting.firm.project.mapper.ProjectMapper;
import com.accounting.firm.reimbursement.dto.ApproveRequest;
import com.accounting.firm.reimbursement.dto.FinanceRequest;
import com.accounting.firm.reimbursement.dto.ReimbursementExportVO;
import com.accounting.firm.reimbursement.dto.ReimbursementItemRequest;
import com.accounting.firm.reimbursement.dto.ReimbursementRequest;
import com.accounting.firm.reimbursement.entity.Reimbursement;
import com.accounting.firm.reimbursement.entity.ReimbursementItem;
import com.accounting.firm.reimbursement.entity.ReimbursementStatus;
import com.accounting.firm.reimbursement.mapper.ReimbursementItemMapper;
import com.accounting.firm.reimbursement.mapper.ReimbursementMapper;
import com.accounting.firm.reimbursement.service.ReimbursementNoGenerator;
import com.accounting.firm.reimbursement.service.ReimbursementService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 报销服务实现：单头 + 明细行 + 生命周期 + 二级审批 + 财务环节
 */
@Slf4j
@Service
public class ReimbursementServiceImpl extends ServiceImpl<ReimbursementMapper, Reimbursement>
        implements ReimbursementService {

    private final ReimbursementItemMapper itemMapper;
    private final ProjectMapper projectMapper;
    private final com.accounting.firm.reimbursement.mapper.ReimbursementAttachmentMapper attachmentMapper;
    private final com.accounting.firm.common.storage.SupabaseStorageService storageService;
    private final DataScopeService dataScopeService;
    private final com.accounting.firm.notify.service.NotifyService notifyService;
    private final SysUserMapper sysUserMapper;

    /** 二级审批阈值（元）：一级批准时超过该金额转终审 */
    @Value("${reimbursement.second-approval-threshold:5000}")
    private BigDecimal secondApprovalThreshold;

    public ReimbursementServiceImpl(ReimbursementItemMapper itemMapper,
                                    ProjectMapper projectMapper,
                                    com.accounting.firm.reimbursement.mapper.ReimbursementAttachmentMapper attachmentMapper,
                                    com.accounting.firm.common.storage.SupabaseStorageService storageService,
                                    DataScopeService dataScopeService,
                                    com.accounting.firm.notify.service.NotifyService notifyService,
                                    SysUserMapper sysUserMapper) {
        this.itemMapper = itemMapper;
        this.projectMapper = projectMapper;
        this.attachmentMapper = attachmentMapper;
        this.storageService = storageService;
        this.dataScopeService = dataScopeService;
        this.notifyService = notifyService;
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public List<ReimbursementItem> listItems(Long reimbursementId, SecurityUser currentUser) {
        checkVisible(reimbursementId, currentUser);
        return itemMapper.selectList(new LambdaQueryWrapper<ReimbursementItem>()
                .eq(ReimbursementItem::getReimbursementId, reimbursementId)
                .orderByAsc(ReimbursementItem::getId));
    }

    @Override
    public List<Reimbursement> recycleList(SecurityUser currentUser) {
        List<Reimbursement> rows = baseMapper.selectDeleted();
        boolean seeAll = currentUser.hasRole("admin") || currentUser.hasRole("finance")
                || dataScopeService.roleLevel(currentUser.getUserId()) >= 2;
        if (seeAll) return rows;
        return rows.stream().filter(b -> isApplicant(b, currentUser)).toList();
    }

    @Override
    public void restore(Long id, SecurityUser currentUser) {
        Reimbursement bill = getById(id);
        if (bill == null || bill.getDeleted() == null || bill.getDeleted() != 1) {
            throw new BusinessException("报销单不在回收站中");
        }
        boolean seeAll = currentUser.hasRole("admin") || currentUser.hasRole("finance")
                || dataScopeService.roleLevel(currentUser.getUserId()) >= 2;
        if (!seeAll && !isApplicant(bill, currentUser)) {
            throw new BusinessException("仅本人或管理员可恢复");
        }
        baseMapper.restoreById(id);
    }

    @Override
    public void checkVisible(Long reimbursementId, SecurityUser currentUser) {
        Reimbursement bill = getById(reimbursementId);
        if (bill == null) {
            throw new BusinessException("报销单不存在");
        }
        if (!visible(bill, currentUser)) {
            throw new BusinessException("资源不存在");
        }
    }

    /**
     * 分层可见性（部门间保持隔离）：
     * 员工 → 仅本人；经理 → 本部门员工/经理的单；合伙人 → 本部门员工/经理的单 + 全所合伙人的单（互看互审）；
     * 经理/合伙人本人的单对同级与上级可见；admin/财务看全部。
     */
    private boolean visible(Reimbursement bill, SecurityUser user) {
        if (user.hasRole("admin") || user.hasRole("finance")) {
            return true;
        }
        int viewerLevel = dataScopeService.roleLevel(user.getUserId());
        Long applicantId = bill.getApplicantId();
        if (viewerLevel >= 3) {
            // 合伙人：全所合伙人的单 + 本部门员工/经理的单
            if (dataScopeService.roleLevel(applicantId) >= 3) {
                return true;
            }
            return sameDept(applicantId, user.getDeptId());
        }
        if (viewerLevel == 2) {
            // 经理：本部门员工/经理的单
            return dataScopeService.roleLevel(applicantId) <= 2 && sameDept(applicantId, user.getDeptId());
        }
        // 员工：仅本人
        return isApplicant(bill, user);
    }

    private boolean sameDept(Long applicantId, Long deptId) {
        if (applicantId == null || deptId == null) return false;
        SysUser applicant = sysUserMapper.selectById(applicantId);
        return applicant != null && deptId.equals(applicant.getDeptId());
    }

    /** 全所合伙人用户 ID 子查询 */
    private static final String PARTNER_IDS_SQL =
            "SELECT u.id FROM sys_user u JOIN sys_user_role ur ON ur.user_id = u.id "
                    + "JOIN sys_role r ON r.id = ur.role_id WHERE r.role_code = 'partner'";

    /** 指定部门、限定角色集合的用户 ID 子查询 */
    private String deptUserLevelsSql(Long deptId, String roleCodes) {
        return "SELECT u.id FROM sys_user u JOIN sys_user_role ur ON ur.user_id = u.id "
                + "JOIN sys_role r ON r.id = ur.role_id WHERE u.dept_id = " + deptId
                + " AND r.role_code IN " + roleCodes;
    }

    @Override
    public PageResult<Reimbursement> pageReimbursements(long current, long size,
                                                        Integer status, String keyword, SecurityUser currentUser) {
        LambdaQueryWrapper<Reimbursement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null, Reimbursement::getStatus, status);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Reimbursement::getApplicantName, keyword)
                    .or().like(Reimbursement::getApplicantUsername, keyword)
                    .or().like(Reimbursement::getTitle, keyword));
        }
        // 分层数据范围：员工仅本人；经理=本部门员工/经理；合伙人=本部门员工/经理 + 全所合伙人；admin/财务全部
        if (!currentUser.hasRole("admin") && !currentUser.hasRole("finance")) {
            int level = dataScopeService.roleLevel(currentUser.getUserId());
            Long deptId = currentUser.getDeptId();
            if (level >= 3) {
                // 本部门员工/经理的单 + 全所合伙人（含自己）的单
                wrapper.and(w -> w
                        .inSql(Reimbursement::getApplicantId, PARTNER_IDS_SQL)
                        .or(deptId != null, w2 -> w2.inSql(Reimbursement::getApplicantId,
                                deptUserLevelsSql(deptId, "('employee','manager','partner')"))));
            } else if (level == 2 && deptId != null) {
                wrapper.and(w -> w
                        .eq(Reimbursement::getApplicantId, currentUser.getUserId())
                        .or(w2 -> w2.inSql(Reimbursement::getApplicantId,
                                deptUserLevelsSql(deptId, "('employee','manager')"))));
            } else {
                wrapper.eq(Reimbursement::getApplicantId, currentUser.getUserId());
            }
        }
        wrapper.orderByDesc(Reimbursement::getCreateTime);
        Page<Reimbursement> page = page(new Page<>(current, size), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDraft(ReimbursementRequest request, SecurityUser currentUser) {
        requireValidProject(request.getProjectId());
        requireValidItemsProjects(request.getItems());
        Reimbursement bill = new Reimbursement();
        bill.setReimbursementNo(generateNo());
        bill.setApplicantId(currentUser.getUserId());
        bill.setApplicantUsername(currentUser.getUsername());
        bill.setApplicantName(currentUser.getNickname());
        bill.setTitle(request.getTitle());
        bill.setProjectId(request.getProjectId());
        bill.setStatus(ReimbursementStatus.DRAFT.getCode());
        bill.setIsInvoiceReceived(false);
        bill.setIsPaid(false);
        save(bill);
        replaceItems(bill.getId(), request.getItems());
        recalculateTotal(bill.getId());
        return bill.getId();
    }

    /** 本轮更新中被移除的明细行 ID（供附件级联清理） */
    private final java.util.List<Long> lastRemovedItemIds = new java.util.ArrayList<>();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDraft(Long id, ReimbursementRequest request, SecurityUser currentUser) {
        lastRemovedItemIds.clear();
        Reimbursement bill = requireEditable(id, currentUser);
        requireValidProject(request.getProjectId());
        requireValidItemsProjects(request.getItems());
        bill.setTitle(request.getTitle());
        bill.setProjectId(request.getProjectId());
        updateById(bill);
        // 增量同步明细行（保留未变更行的 ID 以维持附件关联）
        replaceItems(id, request.getItems());
        recalculateTotal(id);
        // 级联清理被移除明细行的附件
        cleanupAttachmentsOfItems(lastRemovedItemIds);
        lastRemovedItemIds.clear();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitDraft(Long id, SecurityUser currentUser) {
        Reimbursement bill = getById(id);
        if (bill == null) {
            throw new BusinessException("报销单不存在");
        }
        if (!isApplicant(bill, currentUser)) {
            throw new BusinessException("仅申请人可以提交报销单");
        }
        Long itemCount = itemMapper.selectCount(new LambdaQueryWrapper<ReimbursementItem>()
                .eq(ReimbursementItem::getReimbursementId, id));
        if (itemCount == 0) {
            throw new BusinessException("至少需要一条费用明细才能提交");
        }
        // 增值税专用发票必须填写税率
        List<ReimbursementItem> items = itemMapper.selectList(new LambdaQueryWrapper<ReimbursementItem>()
                .eq(ReimbursementItem::getReimbursementId, id));
        for (ReimbursementItem item : items) {
            if ("vat_special".equals(item.getInvoiceType())
                    && (item.getTaxRate() == null || item.getTaxRate().signum() <= 0)) {
                throw new BusinessException("明细「%s」为增值税专用发票，必须填写税率".formatted(item.getCategory()));
            }
        }
        recalculateTotal(id);
        ReimbursementStatus current = ReimbursementStatus.of(bill.getStatus());
        current.submit();
        // 已驳回单重新提交时清空上一轮审批痕迹，避免待审批单据残留旧驳回意见
        if (current == ReimbursementStatus.REJECTED) {
            bill.setPrimaryApproverName(null);
            bill.setApproverUsername(null);
            bill.setApproverName(null);
            bill.setApproveTime(null);
            bill.setApproveComment(null);
        }
        bill.setStatus(ReimbursementStatus.PENDING.getCode());
        updateById(bill);
        notifyApprovers(bill);
    }

    /** 提交后实时通知可见范围内的审批人（申请人同部门有审批权者 + admin + 财务），排除申请人 */
    private void notifyApprovers(Reimbursement bill) {
        try {
            Set<Long> toNotify = new HashSet<>();
            toNotify.addAll(notifyService.userIdsWithRole("admin"));
            toNotify.addAll(notifyService.userIdsWithRole("finance"));
            Long billDept = null;
            if (bill.getApplicantId() != null) {
                SysUser applicant = sysUserMapper.selectById(bill.getApplicantId());
                billDept = applicant == null ? null : applicant.getDeptId();
            }
            List<Long> approverIds = notifyService.userIdsWithPermission("business:reimbursement:approve");
            if (billDept != null) {
                List<Long> deptUserIds = sysUserMapper.selectList(
                                new LambdaQueryWrapper<SysUser>().eq(SysUser::getDeptId, billDept))
                        .stream().map(SysUser::getId).toList();
                approverIds.stream().filter(deptUserIds::contains).forEach(toNotify::add);
            }
            toNotify.remove(bill.getApplicantId());
            String content = "报销单 %s（%s 元，%s）待审批".formatted(
                    bill.getReimbursementNo(), bill.getTotalAmount(), bill.getTitle());
            for (Long approverId : toNotify) {
                notifyService.push(approverId, "reimbursement", bill.getId(),
                        "/business/reimbursement", "报销待审批", content);
            }
        } catch (Exception e) {
            log.warn("提交报销单通知审批人失败: {}", e.getMessage());
        }
    }

    @Override
    public void withdraw(Long id, SecurityUser currentUser) {
        Reimbursement bill = getById(id);
        if (bill == null) {
            throw new BusinessException("报销单不存在");
        }
        if (!isApplicant(bill, currentUser)) {
            throw new BusinessException("仅申请人可以撤回报销单");
        }
        ReimbursementStatus.of(bill.getStatus()).withdraw();
        bill.setStatus(ReimbursementStatus.DRAFT.getCode());
        updateById(bill);
    }

    @Override
    public void deleteDraft(Long id, SecurityUser currentUser) {
        Reimbursement bill = getById(id);
        if (bill == null) {
            throw new BusinessException("报销单不存在");
        }
        if (bill.getStatus() != ReimbursementStatus.DRAFT.getCode()
                && bill.getStatus() != ReimbursementStatus.REJECTED.getCode()) {
            throw new BusinessException("仅草稿或已驳回状态的报销单可删除");
        }
        if (!isApplicant(bill, currentUser)) {
            throw new BusinessException("仅申请人可以删除草稿");
        }
        List<Long> itemIds = itemMapper.selectList(new LambdaQueryWrapper<ReimbursementItem>()
                        .eq(ReimbursementItem::getReimbursementId, id))
                .stream().map(ReimbursementItem::getId).toList();
        removeById(id);
        itemMapper.delete(new LambdaQueryWrapper<ReimbursementItem>()
                .eq(ReimbursementItem::getReimbursementId, id));
        cleanupAttachmentsOfItems(itemIds);
    }

    @Override
    public void approve(Long id, ApproveRequest request, SecurityUser currentUser) {
        Reimbursement bill = getById(id);
        if (bill == null) {
            throw new BusinessException("报销单不存在");
        }
        if (isApplicant(bill, currentUser)) {
            throw new BusinessException("不能审批自己提交的报销单");
        }
        if (!visible(bill, currentUser)) {
            throw new BusinessException("资源不存在");
        }
        ReimbursementStatus current = ReimbursementStatus.of(bill.getStatus());
        boolean finalReview = current == ReimbursementStatus.PENDING_FINAL;
        // 终审仅合伙人或系统管理员可操作
        if (finalReview && !currentUser.hasRole("admin") && !currentUser.hasRole("partner")) {
            throw new BusinessException("待终审单据仅合伙人或系统管理员可终审");
        }
        ReimbursementStatus target = switch (request.getAction()) {
            case "approve" -> ReimbursementStatus.APPROVED;
            case "reject" -> ReimbursementStatus.REJECTED;
            default -> throw new BusinessException("非法的审批动作");
        };

        if (finalReview) {
            current.approveTo(target, true);
        } else {
            current.approveTo(target, false);
            // 一级批准且金额超阈值且审批人非合伙人/管理员 → 转终审
            if (target == ReimbursementStatus.APPROVED
                    && !currentUser.hasRole("admin")
                    && !currentUser.hasRole("partner")
                    && nvl(bill.getTotalAmount()).compareTo(secondApprovalThreshold) > 0) {
                bill.setStatus(ReimbursementStatus.PENDING_FINAL.getCode());
                bill.setPrimaryApproverName(currentUser.getNickname());
                updateById(bill);
                // 通知合伙人/管理员终审
                String finalContent = "报销单 %s（%s 元，%s）一级审批通过，待终审".formatted(
                        bill.getReimbursementNo(), bill.getTotalAmount(), bill.getTitle());
                java.util.Set<Long> finalReviewers = new java.util.HashSet<>(notifyService.userIdsWithRole("partner"));
                finalReviewers.addAll(notifyService.userIdsWithPermission("business:reimbursement:approve"));
                for (Long reviewerId : finalReviewers) {
                    notifyService.push(reviewerId, "reimbursement", bill.getId(),
                            "/business/reimbursement", "报销待终审", finalContent);
                }
                return;
            }
        }
        bill.setStatus(target.getCode());
        bill.setApproverUsername(currentUser.getUsername());
        bill.setApproverName(currentUser.getNickname());
        bill.setApproveTime(LocalDateTime.now());
        bill.setApproveComment(request.getComment());
        updateById(bill);
        // 审批结果实时通知申请人
        if (bill.getApplicantId() != null) {
            String result = target == ReimbursementStatus.APPROVED ? "已批准" : "已驳回";
            String content = "您的报销单 %s（%s 元）%s".formatted(
                    bill.getReimbursementNo(), bill.getTotalAmount(), result);
            if (request.getComment() != null && !request.getComment().isBlank()) {
                content += "，审批意见：" + request.getComment();
            }
            notifyService.push(bill.getApplicantId(), "reimbursement", bill.getId(),
                    "/business/reimbursement", "报销审批结果", content);
        }
    }

    @Override
    public void finance(Long id, FinanceRequest request, SecurityUser currentUser) {
        Reimbursement bill = getById(id);
        if (bill == null) {
            throw new BusinessException("报销单不存在");
        }
        if (!visible(bill, currentUser)) {
            throw new BusinessException("资源不存在");
        }
        if (bill.getStatus() != ReimbursementStatus.APPROVED.getCode()) {
            throw new BusinessException("仅已批准的报销单可进行财务操作");
        }
        switch (request.getAction()) {
            case "receive-invoice" -> bill.setIsInvoiceReceived(true);
            case "mark-paid" -> {
                if (!Boolean.TRUE.equals(bill.getIsInvoiceReceived())) {
                    throw new BusinessException("须先确认收到发票才能标记为已付款");
                }
                bill.setIsPaid(true);
            }
            default -> throw new BusinessException("非法的财务动作");
        }
        updateById(bill);
    }

    @Override
    public List<ReimbursementExportVO> exportItems(LocalDate startDate, LocalDate endDate, SecurityUser currentUser) {
        List<ReimbursementExportVO> rows = baseMapper.selectExportItems(startDate, endDate);
        if (currentUser.hasRole("admin") || currentUser.hasRole("finance")) {
            return rows;
        }
        // 非全局视角：先按范围取可见单号集合，再过滤导出行
        LambdaQueryWrapper<Reimbursement> wrapper = new LambdaQueryWrapper<Reimbursement>()
                .select(Reimbursement::getReimbursementNo);
        var scope = dataScopeService.currentScope();
        switch (scope.type()) {
            case SELF -> wrapper.eq(Reimbursement::getApplicantId, scope.userId());
            case DEPT -> wrapper.and(w -> w
                    .inSql(Reimbursement::getProjectId, scope.projectDeptInSql())
                    .or(w2 -> w2.isNull(Reimbursement::getProjectId)
                            .inSql(Reimbursement::getApplicantId,
                                    "SELECT id FROM sys_user WHERE dept_id = " + scope.deptId())));
            default -> { /* ALL */ }
        }
        Set<String> visibleNos = list(wrapper).stream()
                .map(Reimbursement::getReimbursementNo).collect(java.util.stream.Collectors.toSet());
        return rows.stream().filter(row -> visibleNos.contains(row.getReimbursementNo())).toList();
    }

    /** 校验单据可编辑：存在 + 草稿/已驳回 + 本人 */
    private Reimbursement requireEditable(Long id, SecurityUser currentUser) {
        Reimbursement bill = getById(id);
        if (bill == null) {
            throw new BusinessException("报销单不存在");
        }
        if (bill.getStatus() != ReimbursementStatus.DRAFT.getCode()
                && bill.getStatus() != ReimbursementStatus.REJECTED.getCode()) {
            throw new BusinessException("仅草稿或已驳回状态的报销单可编辑");
        }
        if (!isApplicant(bill, currentUser)) {
            throw new BusinessException("仅申请人可以编辑草稿报销单");
        }
        return bill;
    }

    /**
     * 归属判断：优先按用户 ID 匹配（用户名随邮箱编辑可能变化，快照会失效），
     * 历史数据 applicant_id 为空时回退按用户名忽略大小写比较
     */
    private boolean isApplicant(Reimbursement bill, SecurityUser currentUser) {
        if (bill.getApplicantId() != null) {
            return bill.getApplicantId().equals(currentUser.getUserId());
        }
        return bill.getApplicantUsername() != null
                && bill.getApplicantUsername().equalsIgnoreCase(currentUser.getUsername());
    }

    /** 校验关联项目存在且未归档（可空） */
    private void requireValidProject(Long projectId) {
        if (projectId == null) {
            return;
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("关联项目不存在");
        }
        if (project.getStatus() == ProjectStatus.ARCHIVED.getCode()) {
            throw new BusinessException("项目已归档，不可关联报销");
        }
    }

    /** 校验明细行的归集项目：存在且未归档（可空，行项目优先于单头项目归集成本） */
    private void requireValidItemsProjects(List<ReimbursementItemRequest> items) {
        if (items == null) {
            return;
        }
        for (ReimbursementItemRequest item : items) {
            if (item.getProjectId() != null) {
                requireValidProject(item.getProjectId());
            }
        }
    }

    /** 增量同步明细行：已保存行按 ID 更新、新行插入、缺失行删除；返回被移除的行 ID */
    private List<Long> replaceItems(Long reimbursementId, List<ReimbursementItemRequest> items) {
        List<Long> removedIds = new java.util.ArrayList<>();
        if (items == null) {
            return removedIds;
        }
        // 现有明细行
        List<ReimbursementItem> existing = itemMapper.selectList(
                new LambdaQueryWrapper<ReimbursementItem>()
                        .eq(ReimbursementItem::getReimbursementId, reimbursementId));
        var existingById = existing.stream()
                .collect(java.util.stream.Collectors.toMap(ReimbursementItem::getId, java.util.function.Function.identity()));

        java.util.Set<Long> keptIds = new java.util.HashSet<>();
        for (ReimbursementItemRequest ir : items) {
            ReimbursementItem item;
            if (ir.getId() != null && existingById.containsKey(ir.getId())) {
                // 更新已保存行（保留附件关联）
                item = existingById.get(ir.getId());
            } else {
                item = new ReimbursementItem();
                item.setReimbursementId(reimbursementId);
                item.setCreateTime(LocalDateTime.now());
            }
            item.setCategory(ir.getCategory());
            item.setAmount(ir.getAmount());
            item.setExpenseDate(ir.getExpenseDate());
            item.setDescription(ir.getDescription());
            item.setInvoiceNumber(ir.getInvoiceNumber());
            item.setInvoiceType(StringUtils.hasText(ir.getInvoiceType()) ? ir.getInvoiceType() : "none");
            item.setIsVatInvoice(!"none".equals(item.getInvoiceType()));
            item.setTaxRate(ir.getTaxRate());
            item.setTaxAmount(ir.getTaxAmount());
            item.setProjectId(ir.getProjectId());
            item.setBillable(Boolean.TRUE.equals(ir.getBillable()));
            if (item.getId() == null) {
                itemMapper.insert(item);
            } else {
                itemMapper.updateById(item);
                keptIds.add(item.getId());
            }
        }

        // 删除请求中不存在的行
        for (ReimbursementItem existingItem : existing) {
            if (!keptIds.contains(existingItem.getId())) {
                itemMapper.deleteById(existingItem.getId());
                removedIds.add(existingItem.getId());
            }
        }
        return removedIds;
    }

    /** 级联清理被移除明细行的附件（记录 + 远端对象） */
    private void cleanupAttachmentsOfItems(List<Long> removedItemIds) {
        if (removedItemIds == null || removedItemIds.isEmpty()) {
            return;
        }
        List<com.accounting.firm.reimbursement.entity.ReimbursementAttachment> orphans =
                attachmentMapper.selectList(new LambdaQueryWrapper<com.accounting.firm.reimbursement.entity.ReimbursementAttachment>()
                        .in(com.accounting.firm.reimbursement.entity.ReimbursementAttachment::getItemId, removedItemIds));
        for (var att : orphans) {
            attachmentMapper.deleteById(att.getId());
            try {
                storageService.delete(att.getStoredName());
            } catch (Exception e) {
                log.warn("级联清理附件远端对象失败: {}", att.getStoredName(), e);
            }
        }
    }

    /** 重算并回写单据总金额 */
    private void recalculateTotal(Long reimbursementId) {
        List<ReimbursementItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<ReimbursementItem>()
                        .eq(ReimbursementItem::getReimbursementId, reimbursementId));
        BigDecimal total = items.stream()
                .map(ReimbursementItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Reimbursement bill = getById(reimbursementId);
        bill.setTotalAmount(total);
        updateById(bill);
    }

    /** 生成当日报销编号 */
    private String generateNo() {
        LocalDate today = LocalDate.now();
        String prefix = "BX" + "%1$tY%1$tm%1$td".formatted(today);
        Reimbursement maxBill = lambdaQuery()
                .likeRight(Reimbursement::getReimbursementNo, prefix)
                .orderByDesc(Reimbursement::getReimbursementNo)
                .last("LIMIT 1")
                .one();
        return ReimbursementNoGenerator.next(today, maxBill == null ? null : maxBill.getReimbursementNo());
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
