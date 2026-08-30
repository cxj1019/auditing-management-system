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
