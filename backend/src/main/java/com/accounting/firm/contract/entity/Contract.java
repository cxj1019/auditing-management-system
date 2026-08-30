package com.accounting.firm.contract.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 合同实体
 */
@Data
@TableName("contract")
public class Contract implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属项目 ID（必填，合同必须归属项目） */
    private Long projectId;

    /** 合同编号：HT + yyyyMMdd + 4 位流水，全局唯一 */
    private String contractNo;

    /** 合同名称 */
    private String name;

    /** 合同类型：审计/税务咨询/代理记账/评估等 */
    private String contractType;

    /** 业务类型（业务类型字典明细，决定字号与开票要素） */
    private String bizType;

    /** 合同金额（元） */
    private BigDecimal amount;

    /** 签约日期 */
    private LocalDate signDate;

    /** 服务期限起 */
    private LocalDate serviceStart;

    /** 服务期限止 */
    private LocalDate serviceEnd;

    /** 合同保管人 */
    private String keeperName;

    /** 状态：0-草稿 1-执行中 2-已完成 3-已终止 */
    private Integer status;

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
