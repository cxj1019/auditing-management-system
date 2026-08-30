package com.accounting.firm.confirmation.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 函证实体
 */
@Data
@TableName("confirmation")
public class Confirmation implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 函证编号（人工填写，全局唯一） */
    private String confirmationNo;

    /** 函证类型：银行函证/往来款函证/其他 */
    private String type;

    /** 函证方式：邮寄/电子/现场/其他 */
    private String confirmationMethod;

    /** 被函证单位 */
    private String targetUnit;

    /** 函证内容摘要 */
    private String summary;

    /** 关联项目 ID（可空） */
    private Long projectId;

    /** 项目名称（非数据库字段，查询时填充） */
    @TableField(exist = false)
    private String projectName;

    /** 状态：0-未发出 1-已发出 2-已回函 3-已作废 */
    private Integer status;

    /** 发出日期 */
    private LocalDate sentDate;

    /** 发出快递单号 */
    private String sendTrackingNo;

    /** 回函日期 */
    private LocalDate confirmedDate;

    /** 回函快递单号 */
    private String replyTrackingNo;

    /** 是否回函 */
    private Boolean hasReply;

    /** 回函是否相符 */
    private Boolean replyMatched;

    /** 不符原因 */
    private String discrepancyReason;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 是否逾期（非数据库字段，查询时动态计算：已发出且超阈值未回函） */
    @TableField(exist = false)
    private Boolean overdue = false;
}
