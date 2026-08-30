import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { getToken } from '@/utils/auth'
import { useUserStore } from '@/stores/user'
import { registerModuleRoutes } from './modules'

/**
 * 静态基础路由：登录页、主布局、404
 * 业务模块路由在 modules/ 下定义，登录后按权限动态注册
 */
export const constantRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/',
    name: 'Layout',
    component: () => import('@/layout/index.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '首页', icon: 'HomeFilled', perm: 'dashboard:view' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '404', public: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes: constantRoutes,
})

/** 全局前置守卫：登录校验 + 动态注册模块路由 */
router.beforeEach(async (to) => {
  // 登录页始终放行（注意：404 不能提前放行，否则刷新业务页面时
  // 会因动态路由尚未注册而命中兜底路由，导致永远显示 404）
  if (to.path === '/login') {
    return true
  }

  // 未登录跳转登录页
  const token = getToken()
  if (!token) {
    return { path: '/login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
  }

  // 已登录但未加载用户信息：拉取信息并按权限注册模块路由
  const userStore = useUserStore()
  if (!userStore.infoLoaded) {
    try {
      await userStore.loadUserInfo()
      // 每次会话恢复都重新注册模块路由（registerModuleRoutes 内部会先移除旧路由，
      // 覆盖登出换号、401 重登等权限变化场景）
      registerModuleRoutes(router, userStore.menus)
      // 按路径重新进入目标路由；不能携带 name，
      // 否则会按名称再次命中注册前的 404 兜底路由
      return { path: to.path, query: to.query, hash: to.hash, replace: true }
    } catch {
      // 信息加载失败（令牌失效等）：request 拦截器已跳转登录页
      return false
    }
  }
  return true
})

export default router
