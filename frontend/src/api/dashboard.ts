import request from './request'
import type { DashboardSummary } from '@/types'

/** 工作台聚合数据 */
export function getDashboard(): Promise<DashboardSummary> {
  return request.get('/dashboard')
}
