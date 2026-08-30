package com.accounting.firm.contract.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 合同字号类型字典
 * <p>合同字号按类型自动编号：{前缀}({年份})第{4 位流水}号，
 * 流水按「类型 + 年份」独立递增，如 迈伊兹审约(2026)第0001号。</p>
 */
@Data
@TableName("contract_no_type")
public class ContractNoType implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 字号简称：审/验/咨/代 */
    private String typeChar;

    /** 编号前缀：迈伊兹审约/迈伊兹审验/咨/代 */
    private String prefix;

    /** 对应的合同类型 */
    private String contractType;

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
