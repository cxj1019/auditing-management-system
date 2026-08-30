package com.accounting.firm.contract.entity;

import com.accounting.firm.common.exception.BusinessException;

import java.util.Map;
import java.util.Set;

/**
 * 合同状态枚举与状态机
 * <p>合法流转：草稿 → 执行中；执行中 → 已完成；执行中 → 已终止</p>
 */
public enum ContractStatus {

    /** 草稿 */
    DRAFT(0),
    /** 执行中 */
    RUNNING(1),
    /** 已完成 */
    FINISHED(2),
    /** 已终止 */
    TERMINATED(3);

    private final int code;

    private static final Map<ContractStatus, Set<ContractStatus>> TRANSITIONS = Map.of(
            DRAFT, Set.of(RUNNING),
            RUNNING, Set.of(FINISHED, TERMINATED),
            FINISHED, Set.of(),
            TERMINATED, Set.of()
    );

    ContractStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /** 按数据库存储值解析状态 */
    public static ContractStatus of(int code) {
        for (ContractStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new BusinessException("未知的合同状态: " + code);
    }

    /** 判断是否允许流转到目标状态 */
    public boolean canTransitionTo(ContractStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    /** 流转到目标状态，非法流转抛出业务异常 */
    public ContractStatus transitionTo(ContractStatus target) {
        if (!canTransitionTo(target)) {
            throw new BusinessException("合同状态不允许从「" + this + "」变更为「" + target + "」");
        }
        return target;
    }
}
