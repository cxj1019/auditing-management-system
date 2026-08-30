package com.accounting.firm.confirmation.entity;

import com.accounting.firm.common.exception.BusinessException;

import java.util.Set;

/**
 * 函证状态枚举与状态机
 * <p>合法流转：未发出 → 已发出 → 已回函；未发出/已发出 → 已作废（终态）</p>
 */
public enum ConfirmationStatus {

    /** 未发出 */
    NOT_SENT(0),
    /** 已发出 */
    SENT(1),
    /** 已回函 */
    CONFIRMED(2),
    /** 已作废 */
    VOIDED(3);

    private final int code;

    private static final Set<ConfirmationStatus> FINAL_STATES = Set.of(CONFIRMED, VOIDED);

    ConfirmationStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /** 按数据库存储值解析状态 */
    public static ConfirmationStatus of(int code) {
        for (ConfirmationStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new BusinessException("未知的函证状态: " + code);
    }

    /** 是否为终态（已回函/已作废） */
    public boolean isFinal() {
        return FINAL_STATES.contains(this);
    }

    /** 判断是否允许流转到目标状态 */
    public boolean canTransitionTo(ConfirmationStatus target) {
        if (isFinal()) {
            return false;
        }
        return switch (this) {
            case NOT_SENT -> target == SENT || target == VOIDED;
            case SENT -> target == CONFIRMED || target == VOIDED;
            default -> false;
        };
    }

    /** 流转到目标状态，非法流转抛出业务异常 */
    public ConfirmationStatus transitionTo(ConfirmationStatus target) {
        if (!canTransitionTo(target)) {
            throw new BusinessException("函证状态不允许从「" + this + "」变更为「" + target + "」");
        }
        return target;
    }
}
