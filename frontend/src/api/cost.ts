import request from './request'
import type { ExpenseStatItem, CostOverview, LaborCostItem, LaborCostRequest, PageResult, ProjectProfitItem, ProjectHoursItem } from '@/types'

/** 项目利润表（year=项目年份筛选） */
export function getProjectProfit(keyword?: string, year?: number): Promise<ProjectProfitItem[]> {
  return request.get('/cost/profit', { params: { ...(keyword ? { keyword } : {}), ...(year ? { year } : {}) } })
}

/** 人员工时明细（项目 × 人员，按规则推算） */
export function getProjectHourDetails(keyword?: string, year?: number): Promise<ProjectHoursItem[]> {
  return request.get('/cost/project-hour-details', { params: { ...(keyword ? { keyword } : {}), ...(year ? { year } : {}) } })
}

/** 经营概览 */
export function getCostOverview(): Promise<CostOverview> {
  return request.get('/cost/overview')
}

/** 分页查询人工成本 */
export function pageLaborCosts(params: {
  current: number
  size: number
  contractId?: number
}): Promise<PageResult<LaborCostItem>> {
  return request.get('/cost/labor', { params })
}

/** 登记人工成本 */
export function addLaborCost(data: LaborCostRequest): Promise<void> {
  return request.post('/cost/labor', data)
}

/** 编辑人工成本 */
export function updateLaborCost(id: number, data: LaborCostRequest): Promise<void> {
  return request.put(`/cost/labor/${id}`, data)
}

/** 删除人工成本 */
export function deleteLaborCost(id: number): Promise<void> {
  return request.delete(`/cost/labor/${id}`)
}

/** 项目工时汇总（按规则推算，含部门隔离） */
export function getProjectHours(keyword?: string, year?: number): Promise<ProjectHoursItem[]> {
  return request.get('/cost/project-hours', { params: { ...(keyword ? { keyword } : {}), ...(year ? { year } : {}) } })
}


/** 员工费用统计：已批准报销按 申请人×类别 汇总 */
export function getExpenseStats(year?: number): Promise<ExpenseStatItem[]> {
  return request.get('/cost/expense-stats', { params: { year } })
}
