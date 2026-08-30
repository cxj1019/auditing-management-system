import type { Router, RouteRecordRaw } from 'vue-router'
import type { MenuItem } from '@/types'

/**
 * 业务模块路由注册
 *
 * 模块目录规范：新增业务模块时，在 modules/ 下新建文件（如 contract.ts），
 * 导出 RouteRecordRaw[] 并在下方 moduleRoutes 中合并；路由 meta.perm 对应
 * 菜单管理中配置的权限标识，无权限的用户不会注册该路由。
 *
 * 组件通过 import.meta.glob 按路径动态导入，component 字段与
 * src/views 下的目录路径一致（如 system/user/index）。
 */
const views = import.meta.glob('../../views/**/*.vue')

/** 各业务模块路由定义（新增模块在此追加） */
const moduleRoutes: RouteRecordRaw[] = [
  {
    path: '/business',
    name: 'Business',
    component: () => import('@/layout/index.vue'),
    meta: { title: '业务管理', icon: 'Briefcase' },
    children: [
      {
        path: 'project',
        name: 'BusinessProject',
        component: () => import('@/views/business/project/index.vue'),
        meta: { title: '项目管理', perm: 'business:project:list' },
      },
      {
        path: 'contract',
        name: 'BusinessContract',
        component: () => import('@/views/business/contract/index.vue'),
        meta: { title: '合同管理', perm: 'business:contract:list' },
      },
      {
        path: 'collection',
        name: 'BusinessCollection',
        component: () => import('@/views/business/collection/index.vue'),
        meta: { title: '收款管理', perm: 'business:collection:list' },
      },
      {
        path: 'invoice',
        name: 'BusinessInvoice',
        component: () => import('@/views/business/invoice/index.vue'),
        meta: { title: '发票管理', perm: 'business:invoice:list' },
      },
      {
        path: 'reimbursement',
        name: 'BusinessReimbursement',
        component: () => import('@/views/business/reimbursement/index.vue'),
        meta: { title: '报销管理', perm: 'business:reimbursement:list' },
      },
      {
        path: 'confirmation',
        name: 'BusinessConfirmation',
        component: () => import('@/views/business/confirmation/index.vue'),
        meta: { title: '函证管理', perm: 'business:confirmation:list' },
      },
      {
        path: 'cost',
        name: 'BusinessCost',
        component: () => import('@/views/business/cost/index.vue'),
        meta: { title: '成本分析', perm: 'business:cost:list' },
      },
      {
        path: 'client',
        name: 'BusinessClient',
        component: () => import('@/views/business/client/index.vue'),
        meta: { title: '客户管理', perm: 'business:client:list' },
      },
      {
        path: 'schedule',
        name: 'BusinessSchedule',
        component: () => import('@/views/business/schedule/index.vue'),
        meta: { title: '日程管理', perm: 'business:schedule:list' },
      },
    ],
  },
  {
    path: '/system',
    name: 'System',
    component: () => import('@/layout/index.vue'),
    meta: { title: '系统管理', icon: 'Setting' },
    children: [
      {
        path: 'user',
        name: 'SystemUser',
        component: () => import('@/views/system/user/index.vue'),
        meta: { title: '用户管理', perm: 'system:user:list' },
      },
      {
        path: 'role',
        name: 'SystemRole',
        component: () => import('@/views/system/role/index.vue'),
        meta: { title: '角色管理', perm: 'system:role:list' },
      },
      {
        path: 'menu',
        name: 'SystemMenu',
        component: () => import('@/views/system/menu/index.vue'),
        meta: { title: '菜单管理', perm: 'system:menu:list' },
      },
      {
        path: 'dept',
        name: 'SystemDept',
        component: () => import('@/views/system/dept/index.vue'),
        meta: { title: '部门管理', perm: 'system:dept:list' },
      },
      {
        path: 'dict',
        name: 'SystemDict',
        component: () => import('@/views/system/dict/index.vue'),
        meta: { title: '业务类型字典', perm: 'system:dict:list' },
      },
      {
        path: 'audit',
        name: 'SystemAudit',
        component: () => import('@/views/system/audit/index.vue'),
        meta: { title: '审计日志', perm: 'system:audit:list' },
      },
    ],
  },
]

/** 已注册的动态路由名称（重复注册前先移除，支持换号后权限变化） */
let registeredRouteNames: string[] = []

/**
 * 按用户菜单权限过滤并注册模块路由
 *
 * @param router 路由实例
 * @param userMenus 当前用户可访问的菜单树
 */
export function registerModuleRoutes(router: Router, userMenus: MenuItem[]): void {
  // 移除上次注册的动态路由（换号登录后权限集合可能不同）
  for (const name of registeredRouteNames) {
    if (router.hasRoute(name)) {
      router.removeRoute(name)
    }
  }
  registeredRouteNames = []

  // 用户可访问的菜单路径集合
  const allowedPaths = new Set<string>()
  collectPaths(userMenus, allowedPaths)

  for (const route of moduleRoutes) {
    const children = (route.children || []).filter((child) => {
      const fullPath = `${route.path}/${child.path}`.replace(/\/+/g, '/')
      // 无 perm 要求或用户具备对应菜单权限才注册
      const perm = child.meta?.perm as string | undefined
      if (!perm) return true
      return allowedPaths.has(fullPath)
    })
    if (children.length > 0) {
      router.addRoute({ ...route, children })
      if (route.name) {
        registeredRouteNames.push(route.name as string)
      }
    }
  }
}

/** 递归收集菜单树中的全部路由路径 */
function collectPaths(menus: MenuItem[], set: Set<string>): void {
  for (const menu of menus) {
    if (menu.path) {
      set.add(menu.path)
    }
    if (menu.children && menu.children.length > 0) {
      collectPaths(menu.children, set)
    }
  }
}

/** 按组件路径解析视图组件（供动态菜单使用） */
export function resolveView(component: string): RouteRecordRaw['component'] | undefined {
  const loader = views[`../../views/${component}.vue`]
  return loader as RouteRecordRaw['component'] | undefined
}
