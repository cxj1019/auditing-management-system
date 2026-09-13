import request from './request'
import type { PageResult, ScheduleItem, ScheduleRequest } from '@/types'

export function listSchedules(params: {
  startDate: string
  endDate: string
  projectId?: number
  userId?: number
}): Promise<ScheduleItem[]> {
  return request.get('/schedules', { params })
}

/** 可选设备清单（会议室/公司车辆等） */
export function listScheduleResources(): Promise<{ id: number; name: string; resourceType: string; status: number }[]> {
  return request.get('/schedules/resources')
}

/** 人 × 项目 工时矩阵 */
export function getHoursMatrix(params: { startDate: string; endDate: string }): Promise<{ userId: number; userName: string; projectId: number | null; projectName: string; hours: number }[]> {
  return request.get('/schedules/hours-matrix', { params })
}

/** 确认成员时段工时，返回确认条数 */
export function confirmHours(params: { startDate: string; endDate: string; userId?: number }): Promise<number> {
  return request.post('/schedules/confirm', null, { params })
}

/** 已锁定月份清单 */
export function listLocks(): Promise<string[]> {
  return request.get('/schedules/locks')
}

export function lockMonth(month: string): Promise<void> {
  return request.post('/schedules/locks', null, { params: { month } })
}

export function unlockMonth(month: string): Promise<void> {
  return request.delete('/schedules/locks', { params: { month } })
}

export function getHoursSummary(params: {
  startDate: string
  endDate: string
}): Promise<{ userId: number; memberName: string; totalHours: number }[]> {
  return request.get('/schedules/hours-summary', { params })
}

export function createSchedule(data: ScheduleRequest): Promise<void> {
  return request.post('/schedules', data)
}

export function updateSchedule(id: number, data: ScheduleRequest): Promise<void> {
  return request.put(`/schedules/${id}`, data)
}

export function deleteSchedule(id: number): Promise<void> {
  return request.delete(`/schedules/${id}`)
}

/** 退出日程：仅移除自己这条 */
export function exitSchedule(id: number): Promise<void> {
  return request.delete(`/schedules/${id}/exit`)
}
