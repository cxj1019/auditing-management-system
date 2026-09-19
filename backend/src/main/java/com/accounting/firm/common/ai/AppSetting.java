package com.accounting.firm.common.ai;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 全局键值设置（AI 接口等） */
@Data
@TableName("app_setting")
public class AppSetting implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private String settingKey;

    private String settingValue;

    private String updateBy;

    private LocalDateTime updateTime;
}
