import request from './request'
import type { ExpenseCategoryItem, ExpenseCategoryRequest } from '@/types'

/** 类别清单（报销表单下拉用） */
export function listExpenseCategories(): Promise<ExpenseCategoryItem[]> {
  return request.get('/expense-categories')
}

/** 新增类别（系统管理员） */
export function createExpenseCategory(data: ExpenseCategoryRequest): Promise<ExpenseCategoryItem> {
  return request.post('/expense-categories', data)
}

/** 编辑类别（系统管理员） */
export function updateExpenseCategory(id: number, data: ExpenseCategoryRequest): Promise<ExpenseCategoryItem> {
  return request.put(`/expense-categories/${id}`, data)
}

/** 删除类别（系统管理员） */
export function deleteExpenseCategory(id: number): Promise<void> {
  return request.delete(`/expense-categories/${id}`)
}
