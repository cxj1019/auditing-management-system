import request from './request'
import type { ClientItem, ClientRequest, PageResult } from '@/types'

export function pageClients(params: {
  current: number; size: number; keyword?: string; clientType?: string; deptId?: number
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
