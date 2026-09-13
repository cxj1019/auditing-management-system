import request from './request'
import type { MonthlyTrendItem, HealthCheckItem, DashboardSummary } from '@/types'

/** 工作台聚合数据 */
export function getDashboard(): Promise<DashboardSummary> {
  return request.get('/dashboard')
}


/** 近 12 个月经营趋势 */
export function getMonthlyTrend(): Promise<MonthlyTrendItem[]> {
  return request.get('/cost/monthly-trend')
}

/** 数据体检 */
export function getHealthCheck(): Promise<HealthCheckItem[]> {
  return request.get('/dashboard/health-check')
}
