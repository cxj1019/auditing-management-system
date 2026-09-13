import request from './request'

/** 对公付款状态：0-草稿 1-待审批 2-已批准 3-已驳回 4-已付款 */
export interface VendorPaymentItem {
  id: number
  paymentNo: string
  vendorName: string
  summary?: string
  projectId?: number | null
  projectName?: string | null
  contractId?: number | null
  contractNo?: string | null
  amount: number
  taxRate?: number | null
  taxAmount?: number | null
  amountExTax?: number | null
  paymentDate?: string
  paymentMethod?: string
  invoiceNo?: string
  status: number
  approverName?: string
  approveComment?: string
  paidBy?: string
  creatorName?: string
  createTime?: string
}

export interface VendorPaymentRequest {
  id?: number
  vendorName: string
  summary?: string
  projectId?: number | null
  amount: number
  taxRate?: number | null
  taxAmount?: number | null
  amountExTax?: number | null
  paymentDate: string
  paymentMethod?: string
  invoiceNo?: string
  remark?: string
}

export interface VendorAttachmentItem {
  id: number
  paymentId: number
  fileName: string
  fileSize: number
  contentType?: string
  createBy?: string
}

export function pageVendorPayments(params: {
  current: number
  size: number
  status?: number
  keyword?: string
}): Promise<{ records: VendorPaymentItem[]; total: number }> {
  return request.get('/vendor-payments', { params })
}

export function createVendorPayment(data: VendorPaymentRequest): Promise<number> {
  return request.post('/vendor-payments', data)
}

export function updateVendorPayment(data: VendorPaymentRequest): Promise<void> {
  return request.put('/vendor-payments', data)
}

export function submitVendorPayment(id: number): Promise<void> {
  return request.put(`/vendor-payments/${id}/submit`)
}

export function withdrawVendorPayment(id: number): Promise<void> {
  return request.put(`/vendor-payments/${id}/withdraw`)
}

export function deleteVendorPayment(id: number): Promise<void> {
  return request.delete(`/vendor-payments/${id}`)
}

export function approveVendorPayment(id: number, action: 'approve' | 'reject', comment?: string): Promise<void> {
  return request.put(`/vendor-payments/${id}/approve?action=${action}&comment=${encodeURIComponent(comment || '')}`)
}

export function markVendorPaid(id: number): Promise<void> {
  return request.put(`/vendor-payments/${id}/mark-paid`)
}

export function listVendorAttachments(id: number): Promise<VendorAttachmentItem[]> {
  return request.get(`/vendor-payments/${id}/attachments`)
}

export function uploadVendorAttachment(id: number, file: File): Promise<unknown> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/vendor-payments/${id}/attachments`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  })
}

export function deleteVendorAttachment(id: number, attachmentId: number): Promise<void> {
  return request.delete(`/vendor-payments/${id}/attachments/${attachmentId}`)
}

export async function downloadVendorAttachment(id: number, attachmentId: number, fileName: string): Promise<void> {
  const response = await request.get(`/vendor-payments/${id}/attachments/${attachmentId}/download`, { responseType: 'blob' })
  const blob = (response as unknown as { data?: Blob }).data ?? (response as unknown as Blob)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}
