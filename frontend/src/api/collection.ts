import request from './request'
import type { RechargeLedgerItem, CollectionSummaryItem, PageResult, PaymentItem, PaymentRequest } from '@/types'

/** 分页筛选查询收款记录 */
export function pagePayments(params: {
  current: number
  size: number
  keyword?: string
  startDate?: string
  endDate?: string
}): Promise<PageResult<PaymentItem>> {
  return request.get('/payments', { params })
}

/** 按合同维度汇总收款 */
export function getCollectionSummary(keyword?: string): Promise<CollectionSummaryItem[]> {
  return request.get('/payments/summary', { params: keyword ? { keyword } : {} })
}

/** 登记收款 */
export function addPayment(data: PaymentRequest): Promise<void> {
  return request.post('/payments', data)
}

/** 编辑收款（不可变更所属发票/合同） */
export function updatePayment(id: number, data: PaymentRequest): Promise<void> {
  return request.put(`/payments/${id}`, data)
}

/** 预收核销：将未核销收款关联到同一合同的已开票发票 */
export function writeOffPayment(id: number, invoiceId: number): Promise<void> {
  return request.put(`/payments/${id}/write-off`, null, { params: { invoiceId } })
}

/** 删除收款 */
export function deletePayment(id: number): Promise<void> {
  return request.delete(`/payments/${id}`)
}


/** 垫付台账：按项目归集垫付→开票→收回闭环 */
export function getRechargeLedger(): Promise<RechargeLedgerItem[]> {
  return request.get('/payments/recharge-ledger')
}
