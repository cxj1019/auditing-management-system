import request from './request'
import type { MenuItem } from '@/types'

/** 查询全部菜单树（含按钮权限点） */
export function getMenuTree(): Promise<MenuItem[]> {
  return request.get('/menus')
}

/** 创建菜单/按钮 */
export function createMenu(data: Partial<MenuItem>): Promise<void> {
  return request.post('/menus', data)
}

/** 编辑菜单/按钮 */
export function updateMenu(data: Partial<MenuItem>): Promise<void> {
  return request.put('/menus', data)
}
