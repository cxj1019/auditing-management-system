package com.accounting.firm.reimbursement.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 报销附件实体（发票扫描件等）
 */
@Data
@TableName("reimbursement_attachment")
public class ReimbursementAttachment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属报销单 ID */
    private Long reimbursementId;

    /** 关联费用明细行 ID（NULL 表示挂单整体） */
    private Long itemId;

    /** 原始文件名 */
    private String fileName;

    /** 存储对象路径（Supabase Storage） */
    private String storedName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 内容类型 */
    private String contentType;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
