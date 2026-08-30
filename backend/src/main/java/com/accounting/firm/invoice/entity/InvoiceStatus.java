package com.accounting.firm.invoice.entity;

import com.accounting.firm.common.exception.BusinessException;

/**
 * 发票状态枚举与状态机
 * <p>合法流转：待开票 → 已开票；待开票/已开票 → 已作废（终态）。
 * 已开票且存在收款核销的发票不可作废。</p>
 */
public enum InvoiceStatus {

    /** 待开票 */
    PENDING(0),
    /** 已开票 */
    ISSUED(1),
    /** 已作废 */
    VOIDED(2);

    private final int code;

    InvoiceStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /** 按数据库存储值解析状态 */
    public static InvoiceStatus of(int code) {
        for (InvoiceStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new BusinessException("未知的发票状态: " + code);
    }

    /** 判断是否允许流转到目标状态 */
    public boolean canTransitionTo(InvoiceStatus target) {
        if (this == VOIDED) {
            return false;
        }
        return switch (this) {
            case PENDING -> target == ISSUED || target == VOIDED;
            case ISSUED -> target == VOIDED;
            default -> false;
        };
    }

    /** 流转到目标状态（不合法时抛出业务异常） */
    public InvoiceStatus transitionTo(InvoiceStatus target) {
        if (!canTransitionTo(target)) {
            throw new BusinessException("非法的发票状态流转");
        }
        return target;
    }
}
