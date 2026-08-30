package com.accounting.firm.invoice.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 发票附件实体（发票扫描件）
 */
@Data
@TableName("invoice_attachment")
public class InvoiceAttachment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属发票 ID */
    private Long invoiceId;

    /** 附件类别：scan=发票扫描件 */
    private String attachmentType;

    /** 原始文件名 */
    private String fileName;

    /** 存储路径（Supabase 对象键） */
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
