import request from './request'
import type {
  PageResult,
  ReimbursementAttachmentItem,
  ReimbursementExportItem,
  ReimbursementItem,
  ReimbursementRequest,
} from '@/types'

/** 分页筛选查询报销单 */
export function pageReimbursements(params: {
  current: number
  size: number
  status?: number
  keyword?: string
}): Promise<PageResult<ReimbursementItem>> {
  return request.get('/reimbursements', { params })
}

/** 创建报销单草稿（含明细行），返回草稿 ID */
export function createReimbursement(data: ReimbursementRequest): Promise<number> {
  return request.post('/reimbursements', data)
}

/** 查询报销单明细行清单 */
export function getReimbItems(id: number): Promise<{ id: number; category: string; amount: number; expenseDate: string; description?: string; invoiceNumber?: string; isVatInvoice?: boolean; invoiceType?: string; taxRate?: number; taxAmount?: number; projectId?: number; billable?: boolean }[]> {
  return request.get(`/reimbursements/${id}/items`)
}

/** 更新草稿（替换明细行） */
export function updateReimbursement(id: number, data: ReimbursementRequest): Promise<void> {
  return request.put(`/reimbursements/${id}`, data)
}

/** 提交草稿 */
export function submitReimbursement(id: number): Promise<void> {
  return request.put(`/reimbursements/${id}/submit`)
}

/** 撤回待审批单据 */
export function withdrawReimbursement(id: number): Promise<void> {
  return request.put(`/reimbursements/${id}/withdraw`)
}

/** 删除草稿 */
export function deleteReimbursement(id: number): Promise<void> {
  return request.delete(`/reimbursements/${id}`)
}

/** 审批（一级批准/驳回/转终审；终审仅 admin） */
export function approveReimbursement(id: number, data: { action: 'approve' | 'reject'; comment: string }): Promise<void> {
  return request.put(`/reimbursements/${id}/approve`, data)
}

/** 财务操作：receive-invoice / mark-paid */
export function financeReimbursement(id: number, action: 'receive-invoice' | 'mark-paid'): Promise<void> {
  return request.put(`/reimbursements/${id}/finance`, { action })
}

/** 导出费用明细扁平行 */
export function getExportItems(params: { startDate?: string; endDate?: string }): Promise<ReimbursementExportItem[]> {
  return request.get('/reimbursements/export-items', { params })
}

// ---------- 发票附件 ----------

/** 附件清单 */
export function listReimbAttachments(id: number): Promise<ReimbursementAttachmentItem[]> {
  return request.get(`/reimbursements/${id}/attachments`)
}

/** 上传发票附件（仅本人草稿态；itemId 可选关联明细行；上传走 Supabase 中转放宽超时） */
export function uploadReimbAttachment(id: number, file: File, itemId?: number): Promise<unknown> {
  const formData = new FormData()
  formData.append('file', file)
  const query = itemId ? `?itemId=${itemId}` : ''
  return request.post(`/reimbursements/${id}/attachments${query}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  })
}

/** 删除附件（仅本人草稿态） */
export function deleteReimbAttachment(id: number, attachmentId: number): Promise<void> {
  return request.delete(`/reimbursements/${id}/attachments/${attachmentId}`)
}

/** 下载附件（blob，带鉴权头） */
export async function downloadReimbAttachment(id: number, attachmentId: number, fileName: string): Promise<void> {
  const response = await request.get(`/reimbursements/${id}/attachments/${attachmentId}/download`, {
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
export function getReimbAttPreviewUrl(id: number, attachmentId: number): Promise<string> {
  return request.get(`/reimbursements/${id}/attachments/${attachmentId}/preview-url`)
}
