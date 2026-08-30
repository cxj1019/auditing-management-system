import request from './request'
import type { NotificationItem } from '@/types'

/** 当前用户通知列表与未读数 */
export function listNotifications(limit = 20): Promise<{ list: NotificationItem[]; unread: number }> {
  return request.get('/notifications', { params: { limit } })
}

/** 未读数 */
export function getUnreadCount(): Promise<number> {
  return request.get('/notifications/unread-count')
}

/** 标记已读 */
export function markNotificationRead(id: number): Promise<void> {
  return request.put(`/notifications/${id}/read`)
}

/** 全部已读 */
export function markAllNotificationsRead(): Promise<void> {
  return request.put('/notifications/read-all')
}

/** 手动触发每日提醒扫描 */
export function generateNotifications(): Promise<number> {
  return request.post('/notifications/generate')
}
