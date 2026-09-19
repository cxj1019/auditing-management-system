package com.accounting.firm.reimbursement.service;

import com.accounting.firm.common.api.PageResult;
import com.accounting.firm.common.security.SecurityUser;
import com.accounting.firm.reimbursement.dto.ApproveRequest;
import com.accounting.firm.reimbursement.dto.FinanceRequest;
import com.accounting.firm.reimbursement.dto.ReimbursementExportVO;
import com.accounting.firm.reimbursement.dto.ReimbursementItemRequest;
import com.accounting.firm.reimbursement.dto.ReimbursementRequest;
import com.accounting.firm.reimbursement.entity.Reimbursement;
import com.accounting.firm.reimbursement.entity.ReimbursementItem;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDate;
import java.util.List;

/**
 * 报销服务（单头 + 明细行 + 生命周期 + 二级审批 + 财务环节）
 */
public interface ReimbursementService extends IService<Reimbursement> {

    /** 查询报销单明细行清单（校验当前用户可见性） */
    List<ReimbursementItem> listItems(Long reimbursementId, SecurityUser currentUser);

    /** 回收站：已软删除的报销单（管理员/财务全量，其他人仅本人） */
    List<Reimbursement> recycleList(SecurityUser currentUser);

    /** 从回收站恢复 */
    void restore(Long id, SecurityUser currentUser);

    /** 校验当前用户对该报销单的可见性（附件等子资源入口用），不可见抛异常 */
    void checkVisible(Long reimbursementId, SecurityUser currentUser);

    /** 分页筛选查询报销单（按数据范围隔离：admin/财务全部，部门用户本部门，无部门仅本人） */
    PageResult<Reimbursement> pageReimbursements(long current, long size,
                                                 Integer status, String keyword, SecurityUser currentUser);

    /** 创建草稿（含明细行），返回草稿 ID */
    Long createDraft(ReimbursementRequest request, SecurityUser currentUser);

    /** 更新草稿（仅本人；替换全部明细行并重算总额） */
    void updateDraft(Long id, ReimbursementRequest request, SecurityUser currentUser);

    /** 提交草稿（本人；至少一条明细） */
    void submitDraft(Long id, SecurityUser currentUser);

    /** 撤回待审批单据（本人，回到草稿） */
    void withdraw(Long id, SecurityUser currentUser);

    /** 删除草稿（仅本人） */
    void deleteDraft(Long id, SecurityUser currentUser);

    /** 审批：一级批准/驳回/转终审；待终审单据仅 admin 可终审 */
    void approve(Long id, ApproveRequest request, SecurityUser currentUser);

    /** 财务操作：receive-invoice 标记已收发票 / mark-paid 标记已付款 */
    void finance(Long id, FinanceRequest request, SecurityUser currentUser);

    /** 导出费用明细扁平行（按数据范围隔离） */
    List<ReimbursementExportVO> exportItems(LocalDate startDate, LocalDate endDate, SecurityUser currentUser);
}
