import axios, { AxiosError, AxiosInstance, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, removeToken } from '@/utils/auth'
import type { ApiResult } from '@/types'
import router from '@/router'

/** 业务码：成功 */
export const SUCCESS_CODE = 0
/** 业务码：未认证 */
export const UNAUTHORIZED_CODE = 401

/** Axios 实例：统一注入 Token、统一解包响应、统一错误提示 */
const request: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  // Render 等平台免费实例冷启动约 1 分钟,超时放宽到 60s
  timeout: 60000,
})

/** 请求拦截器：注入 Bearer Token */
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

/** 响应拦截器：统一解包 {code,message,data}；401 清除会话并跳转登录页 */
request.interceptors.response.use(
  (response) => {
    const res = response.data as ApiResult
    // 非标准结构（如文件流）直接返回
    if (res === null || typeof res !== 'object' || !('code' in res)) {
      return response
    }
    if (res.code === SUCCESS_CODE) {
      return res.data as never
    }
    if (res.code === UNAUTHORIZED_CODE) {
      handleUnauthorized()
    }
    ElMessage.error(res.message || '操作失败')
    return Promise.reject(new Error(res.message || '操作失败'))
  },
  (error: AxiosError<ApiResult>) => {
    // HTTP 层 401：会话过期
    if (error.response?.status === UNAUTHORIZED_CODE) {
      handleUnauthorized()
      return Promise.reject(error)
    }
    let message = error.response?.data?.message || ''
    if (error.code === 'ECONNABORTED' || /timeout/i.test(error.message || '')) {
      message = '请求超时：后端可能正在冷启动（免费实例约需 1 分钟），请稍候几秒后重试一次'
    } else if (!error.response) {
      message = `无法连接后端（${error.message}）。请检查后端是否已启动、接口地址是否配置正确`
    } else if (!message) {
      message = `请求失败（HTTP ${error.response.status}）`
    }
    ElMessage.error(message)
    console.error('[API]', error.config?.method?.toUpperCase(), error.config?.url, '→', message)
    return Promise.reject(error)
  },
)

/** 会话失效处理：清除 Token 并跳转登录页 */
function handleUnauthorized(): void {
  removeToken()
  // 避免重复跳转
  if (router.currentRoute.value.path !== '/login') {
    router.push('/login')
  }
}

export default request
