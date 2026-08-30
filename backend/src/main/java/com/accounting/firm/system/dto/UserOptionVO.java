package com.accounting.firm.system.dto;

import lombok.Data;

/**
 * 在册人员选项视图对象（供人员下拉选择）
 */
@Data
public class UserOptionVO {

    private Long id;

    /** 姓名/昵称 */
    private String nickname;

    /** 登录账号（辅助区分重名） */
    private String username;

    private String email;

    /** 所属部门 ID（未分配则为 null） */
    private Long deptId;

    /** 所属部门名称（未分配则为 null） */
    private String deptName;
}
