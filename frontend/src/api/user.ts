import request from './request'
import type { DepartmentItem, PageResult, UserItem, UserOption, UserRequest } from '@/types'

/** 在册人员选项（启用状态用户，供人员下拉选择） */
export function getUserOptions(): Promise<UserOption[]> {
  return request.get('/users/options')
}

/** 部门选项（供下拉选择） */
export function getDepartmentOptions(): Promise<DepartmentItem[]> {
  return request.get('/departments/options')
}

/** 部门列表 */
export function listDepartments(): Promise<DepartmentItem[]> {
  return request.get('/departments')
}

/** 创建部门 */
export function createDepartment(data: { deptName: string; sort?: number }): Promise<void> {
  return request.post('/departments', data)
}

/** 编辑部门 */
export function updateDepartment(data: { id: number; deptName: string; sort?: number }): Promise<void> {
  return request.put('/departments', data)
}

/** 删除部门 */
export function deleteDepartment(id: number): Promise<void> {
  return request.delete(`/departments/${id}`)
}

/** 分页查询用户 */
export function pageUsers(params: {
  current: number
  size: number
  keyword?: string
}): Promise<PageResult<UserItem>> {
  return request.get('/users', { params })
}

/** 创建用户 */
export function createUser(data: UserRequest): Promise<void> {
  return request.post('/users', data)
}

/** 编辑用户 */
export function updateUser(data: UserRequest): Promise<void> {
  return request.put('/users', data)
}
