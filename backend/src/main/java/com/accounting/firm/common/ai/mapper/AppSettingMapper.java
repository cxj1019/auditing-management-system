package com.accounting.firm.common.ai.mapper;

import com.accounting.firm.common.ai.AppSetting;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/** 全局键值设置 Mapper */
@Mapper
public interface AppSettingMapper extends BaseMapper<AppSetting> {
}
