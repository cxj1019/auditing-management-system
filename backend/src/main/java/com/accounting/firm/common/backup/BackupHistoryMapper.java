package com.accounting.firm.common.backup;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 备份历史 Mapper */
@Mapper
public interface BackupHistoryMapper extends BaseMapper<BackupHistory> {
}
