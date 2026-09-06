package com.accounting.firm.project.entity;

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
 * 项目实体（顶层业务维度）
 */
@Data
@TableName("project")
public class Project implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目编号：PRJ + yyyyMMdd + 4 位流水，全局唯一 */
    private String projectNo;

    /** 项目名称 */
    private String name;

    /** 项目类型（业务类型字典口径，如 财务报表审计/涉税服务） */
    private String type;

    /** 项目性质：收入型 / 无收入型 */
    private String bizNature;

    /** 业务类型（字典明细，如 年度审计/代理记账） */
    private String bizType;

    /** 关联客户 ID */
    private Long clientId;

    /** 归属部门 ID（数据权限按此隔离：部门内成员可见可编辑本项目相关事项） */
    private Long deptId;

    /** 客户名称（非数据库字段，联表填充） */
    @TableField(exist = false)
    private String clientName;

    /** 项目合伙人 */
    private String partnerName;

    /** 项目经理 */
    private String managerName;

    /** 项目现场负责人 */
    private String siteLeaderName;

    /** 项目期间起 */
    private LocalDate startDate;

    /** 项目期间止 */
    private LocalDate endDate;

    /** 状态：0-进行中 1-已完成 2-已归档 */
    private Integer status;

    /** 报告文号（审计报告台账） */
    private String reportNo;

    /** 报告出具日期 */
    private LocalDate reportDate;

    /** 签发合伙人 */
    private String reportPartnerName;

    /** 报告备注 */
    private String reportRemark;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
