package com.accounting.firm.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 项目预算工时明细：级别 × 人数 × 每人预算工时；总预算 = Σ(人数 × 每人工时)
 */
@Data
@TableName("project_budget")
public class ProjectBudget implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private Long staffLevelId;

    /** 该级别投入人数 */
    private Integer headcount;

    /** 每人预算工时 */
    private BigDecimal hoursPerPerson;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
