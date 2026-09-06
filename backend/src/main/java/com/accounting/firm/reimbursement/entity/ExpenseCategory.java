package com.accounting.firm.reimbursement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 报销费用类别字典（系统管理员维护）
 */
@Data
@TableName("expense_category")
public class ExpenseCategory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 类别名称 */
    private String name;

    /** 排序号（小在前） */
    private Integer sort;

    /** 1-启用 0-停用（停用后新增报销不可再选，历史单据不受影响） */
    private Integer status;

    private LocalDateTime createTime;
}
