import request from './request'
import type { ContractAttachmentItem, ContractItem, ContractOptionItem, ContractRequest, PageResult } from '@/types'

/** 非草稿合同下拉选项（供发票登记选择，带出项目/客户/开票信息） */
export function getContractOptions(): Promise<ContractOptionItem[]> {
  return request.get('/contracts/options')
}

/** 分页筛选查询合同 */
export function pageContracts(params: {
  current: number
  size: number
  name?: string
  clientName?: string
  ownerName?: string
  status?: number
}): Promise<PageResult<ContractItem>> {
  return request.get('/contracts', { params })
}

/** 创建合同 */
export function createContract(data: ContractRequest): Promise<void> {
  return request.post('/contracts', data)
}

/** 编辑合同基本信息 */
export function updateContract(data: ContractRequest): Promise<void> {
  return request.put('/contracts', data)
}

/** 合同状态流转：status 取值 1-执行中 2-已完成 3-已终止 */
export function changeContractStatus(id: number, status: number): Promise<void> {
  return request.put(`/contracts/${id}/status?status=${status}`)
}

/** 删除合同（仅草稿） */
export function deleteContract(id: number): Promise<void> {
  return request.delete(`/contracts/${id}`)
}

/** 合同附件清单 */
export function listAttachments(contractId: number): Promise<ContractAttachmentItem[]> {
  return request.get(`/contracts/${contractId}/attachments`)
}

/** 上传合同附件（multipart） */
export function uploadAttachment(contractId: number, file: File): Promise<ContractAttachmentItem> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/contracts/${contractId}/attachments`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 下载合同附件（blob，带鉴权头） */
export async function downloadAttachment(contractId: number, attachmentId: number, fileName: string): Promise<void> {
  const response = await request.get(`/contracts/${contractId}/attachments/${attachmentId}/download`, {
    responseType: 'blob',
  })
  // 拦截器对非标准结构返回完整 AxiosResponse，取 data 作为 Blob
  const blob = (response as unknown as { data?: Blob }).data ?? (response as unknown as Blob)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}

/** 删除合同附件 */
export function deleteAttachment(contractId: number, attachmentId: number): Promise<void> {
  return request.delete(`/contracts/${contractId}/attachments/${attachmentId}`)
}

/** 获取附件预览签名 URL */
export function getContractAttPreviewUrl(contractId: number, attachmentId: number): Promise<string> {
  return request.get(`/contracts/${contractId}/attachments/${attachmentId}/preview-url`)
}
