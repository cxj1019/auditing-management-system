package com.accounting.firm.project.entity;

import com.accounting.firm.common.exception.BusinessException;

import java.util.Set;

/**
 * 项目状态枚举与状态机
 * <p>合法流转：进行中 → 已完成；已完成 → 进行中（重开）；已完成 → 已归档；归档为终态</p>
 */
public enum ProjectStatus {

    /** 进行中 */
    IN_PROGRESS(0),
    /** 已完成 */
    FINISHED(1),
    /** 已归档 */
    ARCHIVED(2);

    private final int code;

    private static final Set<ProjectStatus> FINAL_STATES = Set.of(ARCHIVED);

    ProjectStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /** 按数据库存储值解析状态 */
    public static ProjectStatus of(int code) {
        for (ProjectStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new BusinessException("未知的项目状态: " + code);
    }

    /** 是否为终态（已归档） */
    public boolean isFinal() {
        return FINAL_STATES.contains(this);
    }

    /** 判断是否允许流转到目标状态 */
    public boolean canTransitionTo(ProjectStatus target) {
        if (isFinal()) {
            return false;
        }
        return switch (this) {
            case IN_PROGRESS -> target == FINISHED;
            case FINISHED -> target == IN_PROGRESS || target == ARCHIVED;
            default -> false;
        };
    }

    /** 流转到目标状态，非法流转抛出业务异常 */
    public ProjectStatus transitionTo(ProjectStatus target) {
        if (!canTransitionTo(target)) {
            throw new BusinessException("项目状态不允许从「" + this + "」变更为「" + target + "」");
        }
        return target;
    }
}
