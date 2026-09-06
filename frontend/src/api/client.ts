import request from './request'
import type { ClientContactItem, ClientContactRequest, ClientItem, ClientRequest, PageResult } from '@/types'

export function pageClients(params: {
  current: number; size: number; keyword?: string; clientType?: string
}): Promise<PageResult<ClientItem>> {
  return request.get('/clients', { params })
}

export function createClient(data: ClientRequest): Promise<number> {
  return request.post('/clients', data)
}

export function updateClient(data: ClientRequest & { id: number }): Promise<void> {
  return request.put('/clients', data)
}

export function deleteClient(id: number): Promise<void> {
  return request.delete(`/clients/${id}`)
}

// ---------- 客户联系人 ----------

/** 联系人清单 */
export function listClientContacts(clientId: number): Promise<ClientContactItem[]> {
  return request.get(`/clients/${clientId}/contacts`)
}

/** 新增联系人 */
export function addClientContact(clientId: number, data: ClientContactRequest): Promise<ClientContactItem> {
  return request.post(`/clients/${clientId}/contacts`, data)
}

/** 编辑联系人 */
export function updateClientContact(clientId: number, contactId: number, data: ClientContactRequest): Promise<ClientContactItem> {
  return request.put(`/clients/${clientId}/contacts/${contactId}`, data)
}

/** 删除联系人 */
export function deleteClientContact(clientId: number, contactId: number): Promise<void> {
  return request.delete(`/clients/${clientId}/contacts/${contactId}`)
}
