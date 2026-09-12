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
    // 手机端独立布局：底部标签栏导航，与桌面侧边栏布局完全分离
    path: '/m',
    component: () => import('@/layout/MobileLayout.vue'),
    redirect: '/m/home',
    children: [
      {
        path: 'home',
        name: 'MobileHome',
        component: () => import('@/views/mobile/home.vue'),
        meta: { title: '首页' },
      },
      {
        path: 'clients',
        name: 'MobileClients',
        component: () => import('@/views/mobile/clients.vue'),
        meta: { title: '客户' },
      },
      {
        path: 'projects',
        name: 'MobileProjects',
        component: () => import('@/views/mobile/projects.vue'),
        meta: { title: '项目' },
      },
      {
        path: 'contracts',
        name: 'MobileContracts',
        component: () => import('@/views/mobile/contracts.vue'),
        meta: { title: '合同' },
      },
      {
        path: 'collections',
        name: 'MobileCollections',
        component: () => import('@/views/mobile/collections.vue'),
        meta: { title: '收款' },
      },
      {
        path: 'confirmations',
        name: 'MobileConfirmations',
        component: () => import('@/views/mobile/confirmations.vue'),
        meta: { title: '函证' },
      },
      {
        path: 'invoices',
        name: 'MobileInvoices',
        component: () => import('@/views/mobile/invoices.vue'),
        meta: { title: '发票' },
      },
      {
        path: 'fx',
        name: 'MobileFx',
        component: () => import('@/views/mobile/fx.vue'),
        meta: { title: '汇率牌价' },
      },
      {
        path: 'schedule',
        name: 'MobileSchedule',
        component: () => import('@/views/mobile/schedule.vue'),
        meta: { title: '日程' },
      },
      {
        path: 'bills',
        name: 'MobileBills',
        component: () => import('@/views/mobile/bills.vue'),
        meta: { title: '报销单' },
      },
      {
        path: 'reimburse',
        name: 'MobileReimburse',
        component: () => import('@/views/business/reimbursement/mobileSubmit.vue'),
        meta: { title: '我要报销' },
      },
      {
        path: 'approval',
        name: 'MobileApproval',
        component: () => import('@/views/business/reimbursement/mobile.vue'),
        meta: { title: '报销审批' },
      },
      {
        path: 'more',
        name: 'MobileMore',
        component: () => import('@/views/mobile/more.vue'),
        meta: { title: '更多' },
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

/** 手机端自动跳转映射：桌面路径 → 移动页（'/' 与 /dashboard 已由路由重定向到 /dashboard） */
const MOBILE_REDIRECTS: Record<string, string> = {
  '/': '/m/home',
  '/dashboard': '/m/home',
  '/business/reimbursement': '/m/bills',
  '/business/schedule': '/m/schedule',
  '/business/client': '/m/clients',
  '/business/project': '/m/projects',
  '/business/contract': '/m/contracts',
  '/business/collection': '/m/collections',
  '/business/confirmation': '/m/confirmations',
  '/business/invoice': '/m/invoices',
  '/business/fx': '/m/fx',
}

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

  // 手机上统一走移动版：桌面路径自动映射到对应移动页（成本分析等无移动页的不映射）；
  // ?desktop=1 表示用户主动要看电脑版，不拦
  if (window.innerWidth < 768 && to.query.desktop !== '1') {
    const mobileRedirect = MOBILE_REDIRECTS[to.path]
    if (mobileRedirect) {
      return { path: mobileRedirect }
    }
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
