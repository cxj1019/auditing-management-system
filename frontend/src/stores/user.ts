import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { login as loginApi, logout as logoutApi, getUserInfo as getUserInfoApi } from '@/api/auth'
import { getToken, setToken, removeToken } from '@/utils/auth'
import type { LoginRequest, LoginResponse, MenuItem } from '@/types'

/** 用户状态：Token、用户信息、菜单树、按钮权限集合 */
export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(getToken())
  const userId = ref<number | null>(null)
  const username = ref('')
  const nickname = ref('')
  const menus = ref<MenuItem[]>([])
  const permissions = ref<string[]>([])
  const deptId = ref<number | null>(null)
  const roles = ref<string[]>([])
  /** 是否已加载用户信息（用于路由守卫判断） */
  const infoLoaded = ref(false)

  /** 登录：仅建立会话；用户信息与动态路由统一由路由守卫在下次导航时加载注册，
   *  避免登录后跳过守卫导致业务路由未注册（表现为点击菜单 404） */
  async function login(form: LoginRequest): Promise<void> {
    const data = await loginApi(form)
    if (data.token) {
      token.value = data.token
      setToken(data.token)
    }
    infoLoaded.value = false
  }

  /** 拉取当前用户信息（页面刷新后恢复会话数据） */
  async function loadUserInfo(): Promise<void> {
    const data = await getUserInfoApi()
    applyUserInfo(data)
  }

  /** 登出：调用接口使令牌失效，清除本地会话 */
  async function logout(): Promise<void> {
    try {
      await logoutApi()
    } catch {
      // 令牌已失效时忽略
    }
    reset()
  }

  /** 仅清除本地会话（401 时使用） */
  function reset(): void {
    token.value = null
    userId.value = null
    username.value = ''
    nickname.value = ''
    menus.value = []
    permissions.value = []
    deptId.value = null
    roles.value = []
    infoLoaded.value = false
    removeToken()
  }

  /** 判断是否拥有某按钮权限 */
  function hasPermission(perm: string): boolean {
    return permissions.value.includes(perm)
  }

  /** 判断是否拥有某角色（admin/manager/employee） */
  function hasRole(role: string): boolean {
    return roles.value.includes(role)
  }

  function applyUserInfo(data: LoginResponse): void {
    userId.value = data.userId
    username.value = data.username
    nickname.value = data.nickname || data.username
    menus.value = data.menus || []
    permissions.value = data.permissions || []
    deptId.value = data.deptId ?? null
    roles.value = data.roles || []
    infoLoaded.value = true
  }

  return {
    token,
    userId,
    username,
    nickname,
    menus,
    permissions,
    deptId,
    isAdmin: computed(() => roles.value.includes('admin')),
    roles,
    hasRole,
    infoLoaded,
    login,
    loadUserInfo,
    logout,
    reset,
    hasPermission,
  }
})
