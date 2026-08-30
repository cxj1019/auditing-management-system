import request from './request'
import type { BusinessTypeItem } from '@/types'

/** 业务类型字典（项目性质/项目类型/业务类型/字号/开票要素） */
export function listBusinessTypes(bizNature?: string): Promise<BusinessTypeItem[]> {
  return request.get('/business-types', { params: bizNature ? { bizNature } : {} })
}

/** 新增字典项 */
export function createBusinessType(data: Partial<BusinessTypeItem>): Promise<void> {
  return request.post('/business-types', data)
}

/** 编辑字典项 */
export function updateBusinessType(data: Partial<BusinessTypeItem> & { id: number }): Promise<void> {
  return request.put('/business-types', data)
}

/** 删除字典项 */
export function deleteBusinessType(id: number): Promise<void> {
  return request.delete(`/business-types/${id}`)
}
