import request from './request'
import type { ConfirmationAttachmentItem, ConfirmationItem, ConfirmationRequest, PageResult } from '@/types'

/** 分页筛选查询函证 */
export function pageConfirmations(params: {
  current: number
  size: number
  status?: number
  type?: string
  keyword?: string
  projectId?: number
}): Promise<PageResult<ConfirmationItem>> {
  return request.get('/confirmations', { params })
}

/** 登记函证 */
export function createConfirmation(data: ConfirmationRequest): Promise<void> {
  return request.post('/confirmations', data)
}

/** 编辑函证基本信息 */
export function updateConfirmation(data: ConfirmationRequest): Promise<void> {
  return request.put('/confirmations', data)
}

/** 删除函证（仅未发出） */
export function deleteConfirmation(id: number): Promise<void> {
  return request.delete(`/confirmations/${id}`)
}

/** 状态流转：action=send|confirm|void，send/confirm 需带日期 */
export function changeConfirmationStatus(
  id: number,
  action: 'send' | 'confirm' | 'void',
  date?: string,
): Promise<void> {
  const query = date ? `?action=${action}&date=${date}` : `?action=${action}`
  return request.put(`/confirmations/${id}/status${query}`)
}

// ---------- 附件 ----------

/** 附件清单（可按类别筛选） */
export function listConfirmationAttachments(id: number, attachmentType?: string): Promise<ConfirmationAttachmentItem[]> {
  const params = attachmentType ? { attachmentType } : {}
  return request.get(`/confirmations/${id}/attachments`, { params })
}

/** 上传附件（attachmentType=original|reply） */
export function uploadConfirmationAttachment(id: number, attachmentType: string, file: File): Promise<unknown> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/confirmations/${id}/attachments?attachmentType=${attachmentType}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  })
}

/** 删除附件 */
export function deleteConfirmationAttachment(id: number, attachmentId: number): Promise<void> {
  return request.delete(`/confirmations/${id}/attachments/${attachmentId}`)
}

/** 下载附件（blob） */
export async function downloadConfirmationAttachment(id: number, attachmentId: number, fileName: string): Promise<void> {
  const response = await request.get(`/confirmations/${id}/attachments/${attachmentId}/download`, {
    responseType: 'blob',
  })
  const blob = (response as unknown as { data?: Blob }).data ?? (response as unknown as Blob)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}

/** 获取附件预览签名 URL */
export function getConfirmationAttPreviewUrl(id: number, attachmentId: number): Promise<string> {
  return request.get(`/confirmations/${id}/attachments/${attachmentId}/preview-url`)
}

/** 查询物流并截图（action=send|reply） */
export function trackConfirmationLogistics(id: number, action: 'send' | 'reply'): Promise<unknown> {
  return request.post(`/confirmations/${id}/track-logistics?action=${action}`, {}, { timeout: 120000 })
}
