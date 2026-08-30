package com.accounting.firm.system.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户视图对象（不含密码）
 */
@Data
public class UserVO {

    private Long id;

    private String username;

    private String nickname;

    private String email;

    private String phone;

    private Long deptId;

    /** 部门名称 */
    private String deptName;

    private Integer status;

    private LocalDateTime createTime;

    /** 关联角色 ID 集合 */
    private List<Long> roleIds;

    /** 关联角色名称集合 */
    private List<String> roleNames;
}
