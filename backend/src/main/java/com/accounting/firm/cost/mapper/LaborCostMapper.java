package com.accounting.firm.cost.mapper;

import com.accounting.firm.cost.entity.LaborCost;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 人工成本 Mapper
 */
@Mapper
public interface LaborCostMapper extends BaseMapper<LaborCost> {
}
