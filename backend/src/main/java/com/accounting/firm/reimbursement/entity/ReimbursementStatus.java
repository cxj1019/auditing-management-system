package com.accounting.firm.reimbursement.entity;

import com.accounting.firm.common.exception.BusinessException;

/**
 * 报销单状态枚举与状态机
     * <p>合法流转：草稿 → 待审批（提交）；待审批 → 草稿（撤回）；
     * 待审批 → 已批准/已驳回/待终审（一级审批）；待终审 → 已批准/已驳回（终审）；
     * 已驳回 → 待审批（申请人修改后重新提交）；已批准为终态锁定</p>
 */
public enum ReimbursementStatus {

    /** 草稿 */
    DRAFT(0),
    /** 待审批 */
    PENDING(1),
    /** 已批准 */
    APPROVED(2),
    /** 已驳回 */
    REJECTED(3),
    /** 待终审 */
    PENDING_FINAL(4);

    private final int code;

    ReimbursementStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /** 按数据库存储值解析状态 */
    public static ReimbursementStatus of(int code) {
        for (ReimbursementStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new BusinessException("未知的报销状态: " + code);
    }

    /** 是否为终态（已批准/已驳回） */
    public boolean isFinal() {
        return this == APPROVED || this == REJECTED;
    }

    /** 提交：草稿 → 待审批；已驳回 → 待审批（重新提交） */
    public ReimbursementStatus submit() {
        if (this != DRAFT && this != REJECTED) {
            throw new BusinessException("仅草稿或已驳回状态的报销单可以提交");
        }
        return PENDING;
    }

    /** 撤回：待审批 → 草稿 */
    public ReimbursementStatus withdraw() {
        if (this != PENDING) {
            throw new BusinessException("仅待审批状态的报销单可以撤回");
        }
        return DRAFT;
    }

    /**
     * 审批流转
     *
     * @param target      目标状态（APPROVED/REJECTED/PENDING_FINAL）
     * @param finalReview 是否为终审操作（待终审单据的审批）
     */
    public ReimbursementStatus approveTo(ReimbursementStatus target, boolean finalReview) {
        if (finalReview) {
            if (this != PENDING_FINAL) {
                throw new BusinessException("仅待终审状态的报销单可以进行终审");
            }
            if (target != APPROVED && target != REJECTED) {
                throw new BusinessException("非法的审批目标状态");
            }
            return target;
        }
        if (this != PENDING) {
            throw new BusinessException("仅待审批状态的报销单可以审批");
        }
        if (target != APPROVED && target != REJECTED && target != PENDING_FINAL) {
            throw new BusinessException("非法的审批目标状态");
        }
        return target;
    }
}
