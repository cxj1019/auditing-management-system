import request from './request'
import type { LoginRequest, LoginResponse } from '@/types'

/** 登录 */
export function login(data: LoginRequest): Promise<LoginResponse> {
  return request.post('/auth/login', data)
}

/** 登出 */
export function logout(): Promise<void> {
  return request.post('/auth/logout')
}

/** 获取当前用户信息（含菜单与权限） */
export function getUserInfo(): Promise<LoginResponse> {
  return request.get('/auth/info')
}

/** 修改密码（成功后当前令牌失效，需重新登录） */
export function changePassword(data: { oldPassword: string; newPassword: string }): Promise<void> {
  return request.post('/auth/change-password', data)
}
