import request from './request'
import type { RoleItem, RoleRequest } from '@/types'

/** 查询全部角色 */
export function listRoles(): Promise<RoleItem[]> {
  return request.get('/roles')
}

/** 查询角色已分配的菜单 ID */
export function getRoleMenuIds(roleId: number): Promise<number[]> {
  return request.get(`/roles/${roleId}/menus`)
}

/** 创建角色 */
export function createRole(data: RoleRequest): Promise<void> {
  return request.post('/roles', data)
}

/** 编辑角色 */
export function updateRole(data: RoleRequest): Promise<void> {
  return request.put('/roles', data)
}

/** 为角色分配菜单权限 */
export function assignRoleMenus(roleId: number, menuIds: number[]): Promise<void> {
  return request.put(`/roles/${roleId}/menus`, menuIds)
}
