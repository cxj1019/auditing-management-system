package com.accounting.firm.cost.service;

import com.accounting.firm.common.api.PageResult;
import com.accounting.firm.cost.dto.LaborCostRequest;
import com.accounting.firm.cost.dto.OverviewVO;
import com.accounting.firm.cost.dto.ProjectProfitVO;
import com.accounting.firm.cost.entity.LaborCost;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 成本分析服务
 */
public interface CostAnalysisService extends IService<LaborCost> {

    /** 项目利润聚合（含毛利与毛利率计算） */
    List<ProjectProfitVO> projectProfit(String keyword, Integer year);

    /** 项目工时汇总（按规则推算，含部门隔离），供导出 */
    List<java.util.Map<String, Object>> projectHours(String keyword, Integer year);

    /** 人员工时明细（项目 × 人员，含部门隔离），供导出 */
    List<java.util.Map<String, Object>> projectHourDetails(String keyword, Integer year);

    /** 经营概览统计 */
    OverviewVO overview();

    /** 分页查询人工成本 */
    PageResult<LaborCost> pageLaborCosts(long current, long size, Long projectId);

    /** 登记人工成本（同合同同人同月唯一） */
    void addLaborCost(LaborCostRequest request);

    /** 编辑人工成本 */
    void updateLaborCost(Long id, LaborCostRequest request);

    /** 删除人工成本 */
    void deleteLaborCost(Long id);
}
