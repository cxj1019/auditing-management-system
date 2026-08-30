package com.accounting.firm.notify.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 站内通知（定时提醒生成，按 天+类型+关联对象 去重）
 */
@Data
@TableName("sys_notification")
public class SysNotification implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收人用户 ID */
    private Long userId;

    /** 类型：receivable/confirmation/reimbursement/contract */
    private String type;

    private String title;

    private String content;

    /** 点击跳转路由 */
    private String relatedPath;

    /** 关联业务 ID（去重用，无关联为 0） */
    private Long relatedId;

    /** 0-未读 1-已读 */
    private Integer isRead;

    /** 去重日期 */
    private LocalDate dedupDate;

    private LocalDateTime createTime;
}
