package com.accounting.firm.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目参与人员实体
 */
@Data
@TableName("project_member")
public class ProjectMember implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    /** 参与人员姓名 */
    private String memberName;

    /** 角色：合伙人/项目经理/现场负责人/组员 */
    private String memberRole;

    private Integer sort;

    private LocalDateTime createTime;
}
