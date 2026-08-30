package com.accounting.firm.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 业务类型字典
 * <p>项目性质 → 项目类型 → 业务类型 三级业务配置，
 * 携带字号类型（合同编号前缀解析）、收费频度与开票要素（税收编码/分类/发票品名）。</p>
 */
@Data
@TableName("business_type")
public class BusinessType implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目性质：收入型 / 无收入型 */
    private String bizNature;

    /** 项目类型 */
    private String projectType;

    /** 业务类型（同项目类型下唯一） */
    private String bizType;

    /** 业务说明 */
    private String bizDesc;

    /** 字号类型：审/验/咨/代/商（空表示不参与开票编号） */
    private String noChar;

    /** 收费频度：次/月度/季度 */
    private String feeFreq;

    /** 税收编码 */
    private String taxCode;

    /** 税收分类 */
    private String taxClass;

    /** 发票品名 */
    private String invoiceItem;

    /** 排序号 */
    private Integer sort;
}
