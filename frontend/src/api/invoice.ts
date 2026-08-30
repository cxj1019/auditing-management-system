import request from './request'
import type {
  InvoiceAttachmentItem,
  InvoiceItem,
  InvoiceOptionItem,
  InvoiceRequest,
  InvoiceSummaryItem,
  PageResult,
} from '@/types'

/** 分页筛选查询发票 */
export function pageInvoices(params: {
  current: number
  size: number
  keyword?: string
  type?: string
  status?: number
}): Promise<PageResult<InvoiceItem>> {
  return request.get('/invoices', { params })
}

/** 已开票发票下拉选项（供收款核销选择） */
export function getInvoiceOptions(keyword?: string): Promise<InvoiceOptionItem[]> {
  return request.get('/invoices/options', { params: keyword ? { keyword } : {} })
}

/** 按发票维度核销汇总 */
export function getInvoiceSummary(keyword?: string): Promise<InvoiceSummaryItem[]> {
  return request.get('/payments/invoice-summary', { params: keyword ? { keyword } : {} })
}

/** 登记发票 */
export function createInvoice(data: InvoiceRequest): Promise<void> {
  return request.post('/invoices', data)
}

/** 编辑发票（不可变更所属合同） */
export function updateInvoice(id: number, data: InvoiceRequest): Promise<void> {
  return request.put('/invoices', { ...data, id })
}

/** 删除发票（仅待开票且无核销） */
export function deleteInvoice(id: number): Promise<void> {
  return request.delete(`/invoices/${id}`)
}

/** 状态流转：issue 开票 / void 作废 */
export function changeInvoiceStatus(id: number, action: 'issue' | 'void', invoiceDate?: string): Promise<void> {
  return request.put(`/invoices/${id}/status`, null, { params: { action, invoiceDate } })
}

/** 发票附件清单 */
export function listInvoiceAttachments(invoiceId: number): Promise<InvoiceAttachmentItem[]> {
  return request.get(`/invoices/${invoiceId}/attachments`)
}

/** 上传发票扫描件 */
export function uploadInvoiceAttachment(invoiceId: number, file: File): Promise<unknown> {
  const form = new FormData()
  form.append('file', file)
  return request.post(`/invoices/${invoiceId}/attachments`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 下载发票附件 */
export function downloadInvoiceAttachment(invoiceId: number, attachmentId: number, fileName: string): Promise<void> {
  return request
    .get(`/invoices/${invoiceId}/attachments/${attachmentId}/download`, { responseType: 'blob' })
    .then((blob: unknown) => {
      const url = window.URL.createObjectURL(blob as Blob)
      const link = document.createElement('a')
      link.href = url
      link.download = fileName
      link.click()
      window.URL.revokeObjectURL(url)
    })
}

/** 获取附件预览签名 URL */
export function getInvoiceAttPreviewUrl(invoiceId: number, attachmentId: number): Promise<string> {
  return request.get(`/invoices/${invoiceId}/attachments/${attachmentId}/preview-url`)
}

/** 删除发票附件 */
export function deleteInvoiceAttachment(invoiceId: number, attachmentId: number): Promise<void> {
  return request.delete(`/invoices/${invoiceId}/attachments/${attachmentId}`)
}
