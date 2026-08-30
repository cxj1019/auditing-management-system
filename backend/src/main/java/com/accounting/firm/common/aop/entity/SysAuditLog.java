package com.accounting.firm.common.aop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审计日志（@AuditLog 切面落库）
 */
@Data
@TableName("sys_audit_log")
public class SysAuditLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String operation;

    /** 成功 / 失败 */
    private String result;

    private String errorMsg;

    private Long costMs;

    private String ip;

    private LocalDateTime createTime;
}
