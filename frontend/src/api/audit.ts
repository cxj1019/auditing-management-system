import request from './request'
import type { AuditLogItem, PageResult } from '@/types'

/** 分页查询审计日志（管理员） */
export function pageAuditLogs(params: {
  current: number
  size: number
  username?: string
  keyword?: string
  startDate?: string
  endDate?: string
}): Promise<PageResult<AuditLogItem>> {
  return request.get('/audit-logs', { params })
}
